package org.kfh.aiops.platform.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityAuthController {

    private final IdentityAuthService identityAuthService;

    public IdentityAuthController(IdentityAuthService identityAuthService) {
        this.identityAuthService = identityAuthService;
    }

    @PostMapping("/sign-in")
    public IdentitySignInResponse signIn(@Valid @RequestBody IdentitySignInRequest request) {
        return identityAuthService.signIn(request);
    }
}

