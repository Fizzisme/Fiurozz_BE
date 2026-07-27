import { Injectable } from '@nestjs/common';
import {hash} from "bcrypt";
import { PrismaService } from '../../prisma/prisma.service.js';

@Injectable()
export class RefreshTokenService {

    constructor(
        private readonly prisma: PrismaService,
    ) {}

    async save(
        userId: string,
        refreshToken: string,
        deviceName: string,
        userAgent: string,
        ipAddress: string,
    ) {

        const tokenHash = await hash(refreshToken, 10);

        return this.prisma.refreshToken.create({

            data: {

                userId,

                tokenHash,

                expiresAt: new Date(
                    Date.now() +
                    7 * 24 * 60 * 60 * 1000,
                ),

                deviceName,
                userAgent,
                ipAddress,

            },

        });

    }

}