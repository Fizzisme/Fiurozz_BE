import { Module } from '@nestjs/common';
import {ConfigModule} from "@nestjs/config";
import { AuthModule } from './auth/auth.module.js';
import { PrismaModule } from './prisma/prisma.module.js';
@Module({
  imports: [
      ConfigModule.forRoot({
        isGlobal: true,
        envFilePath: '.env',
      }),
      AuthModule,
      PrismaModule,
  ],
})
export class AppModule {}
