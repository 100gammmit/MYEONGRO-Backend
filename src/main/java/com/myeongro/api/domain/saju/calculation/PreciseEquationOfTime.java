package com.myeongro.api.domain.saju.calculation;

import java.time.Instant;

/** Meeus equations used by NOAA main.js. UTC JD, minutes of apparent minus mean solar time.
 * This is an identified approximation, not a claim of observation-level accuracy.
 * Source: https://gml.noaa.gov/grad/solcalc/main.js
 */
public final class PreciseEquationOfTime {
	private PreciseEquationOfTime() { }
	public static double julianDate(Instant instant) {
		return 2440587.5 + instant.getEpochSecond() / 86400d + instant.getNano() / 86400e9;
	}
	public static double minutes(Instant instant) {
		double t = (julianDate(instant) - 2451545d) / 36525d;
		double l = radians(wrap(280.46646 + t * (36000.76983 + t * 0.0003032)));
		double m = radians(357.52911 + t * (35999.05029 - 0.0001537 * t));
		double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
		double seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813));
		double epsilon = 23 + (26 + seconds / 60) / 60 + 0.00256 * Math.cos(radians(125.04 - 1934.136 * t));
		double y = Math.pow(Math.tan(radians(epsilon) / 2), 2);
		return 4 * Math.toDegrees(y * Math.sin(2 * l) - 2 * e * Math.sin(m)
			+ 4 * e * y * Math.sin(m) * Math.cos(2 * l) - 0.5 * y * y * Math.sin(4 * l)
			- 1.25 * e * e * Math.sin(2 * m));
	}
	private static double radians(double degrees) { return Math.toRadians(degrees); }
	private static double wrap(double degrees) { return (degrees % 360 + 360) % 360; }
}
