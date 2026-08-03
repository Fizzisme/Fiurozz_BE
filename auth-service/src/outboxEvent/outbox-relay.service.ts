import { Injectable, Logger } from '@nestjs/common';
import { Interval } from '@nestjs/schedule';
import { PrismaService } from '../prisma/prisma.service.js';
import { AmqpConnection } from '@golevelup/nestjs-rabbitmq';
import {OutboxEventService} from "./outbox-event.service.js";


// Implements the relay (publish) side of the Outbox pattern: polls
// the outbox table on a fixed interval and publishes any pending
// events to RabbitMQ, marking each as processed on success.
@Injectable()
export class OutboxRelayService {
    private readonly logger = new Logger(OutboxRelayService.name);

    constructor(
        private readonly prisma: PrismaService,
        private readonly amqpConnection: AmqpConnection,
        private readonly outboxEvent: OutboxEventService
    ) {}

    @Interval(2000)
    async relay() {
        const events = await this.outboxEvent.findAll();

        for (const event of events) {
            try {
                await this.amqpConnection.publish(
                    'user.events',
                    event.eventType,
                    event.payload,
                    { persistent: true }, // survive a broker restart, not just held in memory
                );

                // Mark as processed only AFTER a successful publish, so a
                // crash between publish and this update just results in
                // the event being republished next tick (duplicate, not lost) —
                // consumers are expected to be idempotent (see ConsumerService).
                await this.prisma.outboxEvent.update({
                    where: { id: event.id },
                    data: { processedAt: new Date() },
                });

                this.logger.log(`Published event ${event.id} (${event.eventType})`);
            } catch (err) {
                // Publish failed (broker down, network blip...) — leave
                // processedAt null so it's picked up again next tick,
                // just track the attempt count for visibility/alerting.
                await this.prisma.outboxEvent.update({
                    where: { id: event.id },
                    data: { attempts: { increment: 1 } },
                });
                this.logger.error(`Failed to publish event ${event.id}: ${err.message}`);
            }
        }
    }
}