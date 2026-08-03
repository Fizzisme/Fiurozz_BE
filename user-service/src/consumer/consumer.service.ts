import { RabbitSubscribe } from '@golevelup/nestjs-rabbitmq';
import {PrismaService} from "../prisma/prisma.service.js";
import {Injectable} from "@nestjs/common";

interface AccountCreatedPayload {
    id: string;
    email: string;
    fullName: string;
    displayName: string;
}

@Injectable()
export class UserEventsConsumer {
    constructor(private readonly prisma: PrismaService) {}

    @RabbitSubscribe({
        exchange: 'user.events',
        routingKey: 'account.created',
        queue: 'user-service.account-created',
        queueOptions: { durable: true },
    })
    async handleAccountCreated(payload: AccountCreatedPayload) {
        await this.prisma.user.create({
            data: {
                id: payload.id,
                profile: {
                    create: {
                        username: payload.email.split('@')[0],
                        displayName: payload.displayName,
                        fullName: payload.fullName,
                    },
                },
                settings: { create: {} },
            },
        });

        console.log(`Đã tạo user cho account ${payload.id}`);
    }
}