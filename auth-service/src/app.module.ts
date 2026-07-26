import { Module } from '@nestjs/common';
import {ConfigModule} from "@nestjs/config";
import { PrismaService } from "./prisma.service.js";
@Module({
  imports: [
      ConfigModule.forRoot({
        isGlobal: true,
        envFilePath: '.env',
      }),
  ],
  providers: [PrismaService],
})
export class AppModule {}
