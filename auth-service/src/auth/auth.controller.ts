import {Body, Controller, Post, Res, Req} from '@nestjs/common';

import {AuthService} from "./auth.service.js";
import {RegisterDto} from "./dto/register.dto.js";
import {LoginDto}  from "./dto/login.dto.js";
import type { Request, Response } from 'express';

/**
 * Handles authentication endpoints: registration, login, and
 * access-token refresh. Mounted at the service root (no controller
 * prefix), since routing/prefixing (e.g. "/auth") is handled by the
 * API Gateway upstream.
 */
@Controller()
export class AuthController {
    constructor(private readonly authService: AuthService) {}

    // Creates a new user account.
    @Post('register')
    register (@Body() dto: RegisterDto) {
        return this.authService.register(dto);
    }

    // Authenticates credentials and issues tokens. Uses passthrough
    // mode so the service can set cookies (e.g. refresh token) on
    // res directly while Nest still sends the returned body as the
    // JSON response.
    @Post('login')
    login (@Body() dto: LoginDto, @Res({ passthrough: true }) res: Response){
        return this.authService.login(dto, res)
    }

    // Issues a new access token using the refresh token (read from
    // the request, likely a cookie set during login) and rotates it
    // in the response if applicable.
    @Post('refresh')
    refresh(@Req() req: Request, @Res({ passthrough: true }) res: Response){
        return this.authService.refresh(req, res)
    }

}
