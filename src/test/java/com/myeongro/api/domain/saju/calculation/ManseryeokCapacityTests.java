package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;

class ManseryeokCapacityTests {
	@Test void twoSlotsHaveNoQueueAndAreReleasedAfterFailure() throws Exception {
		var engine=mock(SajuCalculationService.class);
		var entered=new CountDownLatch(2); var release=new CountDownLatch(1);
		when(engine.calculateForChart(anyMap(),anyInt(),anyDouble(),any(),any(),any())).thenAnswer(call->{
			entered.countDown(); if (!release.await(2,TimeUnit.SECONDS)) throw new AssertionError("release timed out");
			throw new IllegalStateException("synthetic internal failure");
		});
		var service=new ManseryeokCalculationService(engine,ManseryeokCalculationTests.CATALOG,new ManseryeokChartBuilder(),new SajuMonthlyCalendarService(),ManseryeokCalculationTests.MAPPER);
		var request=ManseryeokCalculationTests.request("2000-06-15","14:30","exact",null);
		try(var pool=Executors.newFixedThreadPool(2)) {
			var first=pool.submit(()->assertThatThrownBy(()->service.calculate(request)).isInstanceOf(ManseryeokCalculationService.Failed.class));
			var second=pool.submit(()->assertThatThrownBy(()->service.calculate(request)).isInstanceOf(ManseryeokCalculationService.Failed.class));
			assertThat(entered.await(1,TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(()->service.calculate(request)).isInstanceOfSatisfying(ManseryeokCalculationService.Unavailable.class,e->assertThat(e.code()).isEqualTo("SAJU_CALCULATION_BUSY"));
			release.countDown(); first.get(2,TimeUnit.SECONDS); second.get(2,TimeUnit.SECONDS);
		}
		assertThatThrownBy(()->service.calculate(request)).isInstanceOf(ManseryeokCalculationService.Failed.class);
		verify(engine,times(3)).calculateForChart(anyMap(),anyInt(),anyDouble(),any(),any(),any());
	}
	@Test void deadlineIsPassedToCandidateWorkAndReturnsAnUnavailableResult() throws Exception {
		var engine=mock(SajuCalculationService.class);
		when(engine.calculateForChart(anyMap(),anyInt(),anyDouble(),any(),any(),any())).thenAnswer(call->{
			Runnable deadline=call.getArgument(5);
			while(true) { deadline.run(); Thread.sleep(10); }
		});
		var service=new ManseryeokCalculationService(engine,ManseryeokCalculationTests.CATALOG,new ManseryeokChartBuilder(),new SajuMonthlyCalendarService(),ManseryeokCalculationTests.MAPPER);
		long start=System.nanoTime();
		assertThatThrownBy(()->service.calculate(ManseryeokCalculationTests.request("2000-06-15","14:30","exact",null)))
			.isInstanceOfSatisfying(ManseryeokCalculationService.Unavailable.class,e->assertThat(e.code()).isEqualTo("SAJU_CALCULATION_DEADLINE"));
		assertThat(Duration.ofNanos(System.nanoTime()-start).toMillis()).isBetween(2900L,4000L);
	}
	@Test void thirtySyntheticCalculationsIncludeApproximateUnknownAndTwoConcurrentRequests() throws Exception {
		var service=ManseryeokCalculationTests.service(); var durations=new ConcurrentHashMap<String,List<Long>>();
		try(var pool=Executors.newFixedThreadPool(2)) {
			var work=new ArrayList<Callable<Void>>();
			for(int i=0;i<30;i++) { final int sample=i;
				work.add(()->{
					String precision=switch(sample%3){case 0->"exact";case 1->"approximate";default->"unknown";};
					var request=ManseryeokCalculationTests.request(sample%2==0 ? "2000-06-15":"1988-10-09",precision.equals("unknown")?null:"02:30",precision,null);
					long start=System.nanoTime(); var result=service.calculate(request); long elapsed=System.nanoTime()-start;
					durations.computeIfAbsent(precision,k->Collections.synchronizedList(new ArrayList<>())).add(elapsed);
					assertThat(result.monthlyCalendar().months()).hasSize(12); assertThat(elapsed).isLessThan(3_000_000_000L);
					return null;
				});
			}
			for(var result:pool.invokeAll(work)) result.get();
		}
		durations.forEach((precision,times)->{
			assertThat(times).hasSize(10);
			var sorted=times.stream().sorted().toList();
			System.out.printf(Locale.ROOT,"MANSERYEOK_PERFORMANCE precision=%s count=10 medianMs=%.1f p95Ms=%.1f maxMs=%.1f java=%s processors=%d%n",precision,sorted.get(5)/1e6,sorted.get(9)/1e6,sorted.getLast()/1e6,System.getProperty("java.version"),Runtime.getRuntime().availableProcessors());
		});
	}
}
