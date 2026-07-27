import { IsEmail, IsString, MinLength } from 'class-validator';


export class LoginDto {
    @IsEmail()
    email: string;

    @IsString()
    @MinLength(8)
    password: string;

    @IsString()
    deviceName: string;

    @IsString()
    userAgent: string;

    @IsString()
    ipAddress: string;
}