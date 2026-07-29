import { NestFactory } from '@nestjs/core';
import {AppModule} from "./app.module.js";
import {ValidationPipe} from "@nestjs/common";
import cookieParser from "cookie-parser";

/**
 * Application entry point for the Auth Service.
 * Bootstraps the Nest application, configures global request
 * validation and cookie parsing, then starts the HTTP listener.
 */
async function bootstrap() {
  const app = await NestFactory.create(AppModule);

    // Global validation pipe applied to every incoming request body/
    // query/params (validated against DTO class-validator decorators):
    // - whitelist: strips properties not defined in the DTO
    // - forbidNonWhitelisted: rejects the request (400) if it contains
    //   properties not defined in the DTO, instead of silently dropping them
    // - transform: automatically converts payloads into instances of
    //   their DTO classes (e.g. string -> number) based on type metadata
  app.useGlobalPipes(new ValidationPipe(
      {
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }
  ));

    // Parses the Cookie header into req.cookies, needed for reading/
    // issuing auth-related cookies (e.g. refresh token).
  app.use(cookieParser());

  await app.listen(process.env.PORT ?? 3000);
}
bootstrap();
