package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;
import java.util.UUID;

public record ProvisionedOAuthUser(
	UUID userId
) implements Serializable {
}
