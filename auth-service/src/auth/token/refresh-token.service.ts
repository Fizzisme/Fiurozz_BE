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
        jti: string,
        refreshToken: string,
        deviceName?: string,
        userAgent?: string,
        ipAddress?: string,
    ) {

        const tokenHash = await hash(refreshToken, 10);

        return this.prisma.refreshToken.create({

            data: {

                userId,

                tokenHash,

                jti,

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

    async findByJti(jti: string) {
        return this.prisma.refreshToken.findUnique({where: { jti }});
    }

    async deleteByJti(jti: string) {
        return this.prisma.refreshToken.delete({where: { jti }});
    }

    async getSessions(userId: string) {
        return this.prisma.refreshToken.findMany({
            where: {
                userId,
                revoked: false,
            },
            orderBy: {
                lastUsedAt: "desc",
            },
            select: {
                id: true,
                deviceName: true,
                userAgent: true,
                ipAddress: true,
                createdAt: true,
                lastUsedAt: true,
                expiresAt: true,
            },
        });
    }

    async deleteSession(
        sessionId: string,
        userId: string,
    ) {
        return this.prisma.refreshToken.deleteMany({
            where: {
                id: sessionId,
                userId,
            },
        });
    }

    async deleteAllSessions(userId: string) {
        return this.prisma.refreshToken.deleteMany({
            where: {
                userId,
            },
        });
    }
}