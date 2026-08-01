import {Body, Controller, Post, Res, Req, Get, UseGuards, Param, Delete} from '@nestjs/common';

import {AuthService} from "./auth.service.js";
import {RegisterDto} from "./dto/register.dto.js";
import {LoginDto}  from "./dto/login.dto.js";
import type { Request, Response } from 'express';
import {GoogleGuard} from "../oauth-account/guards/google.guard.js";
import {GithubGuard} from "../oauth-account/guards/github.guard.js";


/**
 * Handles authentication endpoints: registration, login, and
 * access-token refresh. Mounted at the service root (no controller
 * prefix), since routing/prefixing (e.g. "/auth") is handled by the
 * API Gateway upstream.
 */
@Controller()
export class AuthController {
    constructor(private readonly authService: AuthService) {}

    // Creates a new account account.
    @Post('register')
    register (@Body() dto: RegisterDto) {
        return this.authService.register(dto);
    }

    // Authenticates credentials and issues tokens. Uses passthrough
    // mode so the service can set cookies (e.g. refresh token) on
    // res directly while Nest still sends the returned body as the
    // JSON response.
    @Post('login')
    login (@Body() dto: LoginDto, @Req() req: Request, @Res({ passthrough: true }) res: Response){
        return this.authService.login(dto, req, res)
    }

    // Issues a new access token using the refresh token (read from
    // the request, likely a cookie set during login) and rotates it
    // in the response if applicable.
    @Post('refresh')
    refresh(@Req() req: Request, @Res({ passthrough: true }) res: Response){
        return this.authService.refresh(req, res)
    }

    @Get('oauth/google')
    @UseGuards(GoogleGuard)
    googleLogin(){}

    @Get("oauth/google/callback")
    @UseGuards(GoogleGuard)
    googleCallback(@Req() req: Request, @Res({ passthrough: true }) res:Response) {
        return this.authService.oauthLogin(req, res)
    }

    @Get("oauth/github")
    @UseGuards(GithubGuard)
    githubLogin() {}

    @Get("oauth/github/callback")
    @UseGuards(GithubGuard)
    githubCallback(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
    ) {
        return this.authService.oauthLogin(req, res);
    }

    @Post("logout")
    logout(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
    ) {
        return this.authService.logout(req, res);
    }

    @Get("sessions")
    getSessions(
        @Req() req: Request,
    ) {
        return this.authService.getSessions(req);
    }

    @Delete("sessions/:id")
    logoutSession(
        @Param("id") id: string,
        @Req() req: Request,
    ) {
        return this.authService.logoutSession(
            id,
            req,
        );
    }

    @Post("logout-all")
    logoutAll(
        @Req() req: Request,
    ) {
        return this.authService.logoutAll(req);
    }
}
