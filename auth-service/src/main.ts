import { NestFactory } from '@nestjs/core';
import {AppModule} from "./app.module.js";
import {ValidationPipe} from "@nestjs/common";


async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  console.log(process.env.PORT)

  app.useGlobalPipes(new ValidationPipe(
      {
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }
  ));


  await app.listen(process.env.PORT ?? 3000);
}
bootstrap();
