import {BadRequestException, Injectable} from '@nestjs/common';
import {RegisterDto} from "./dto/register.dto.js";
import {PrismaService} from "../prisma/prisma.service.js";
import * as bcrypt from 'bcrypt';

@Injectable()
export class AuthService {
    constructor(private readonly prismaService: PrismaService) {}
     async register (data: RegisterDto) {

        const existed = await this.prismaService.user.findUnique({
            where: {
                email: data.email,
            }
        })

        if (existed) {
            throw new BadRequestException('Email already exists');
        }

        const passwordHash = await bcrypt.hash(data.password, 10);

        const user = await this.prismaService.user.create({
            data: {
                email: data.email,
                fullName: data.fullName,
                displayName: data.displayName,
                passwordHash: passwordHash,
            },
        })

         return {
             message: 'Register successfully',
             user,
         };
    }
}
