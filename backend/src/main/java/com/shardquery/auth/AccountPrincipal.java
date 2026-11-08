package com.shardquery.auth;

import java.io.Serializable;
import java.util.UUID;

public record AccountPrincipal(UUID id, String name, String email) implements Serializable {}
