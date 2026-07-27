import {Body, Controller, Post} from '@nestjs/common';

import {AuthService} from "./auth.service.js";
import {RegisterDto} from "./dto/register.dto.js";

@Controller()
export class AuthController {
    constructor(private readonly authService: AuthService) {}

    @Post('register')
    register (@Body() dto: RegisterDto) {
        console.log(dto)
        return this.authService.register(dto);
    }
}
