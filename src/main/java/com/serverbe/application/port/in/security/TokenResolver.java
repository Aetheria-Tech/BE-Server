package com.serverbe.application.port.in.security;

import java.util.List;

public interface TokenResolver {
    boolean validateToken(String token);
    Long getIdFromToken(String token);
    List<String> getRolesFromToken(String token);

}
