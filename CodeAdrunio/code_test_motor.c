#include <AFMotor.h>

// M1–M2 (Timer5) dùng MOTOR12_xxx, M3–M4 (Timer1) dùng MOTOR34_xxx
AF_DCMotor motorLeftFront(  1, MOTOR12_1KHZ);  // M1, PWM Timer5
AF_DCMotor motorRightFront( 2, MOTOR12_1KHZ);  // M2, PWM Timer5
AF_DCMotor motorLeftRear(   3, MOTOR34_1KHZ);  // M3, PWM Timer1
AF_DCMotor motorRightRear(  4, MOTOR34_1KHZ);  // M4, PWM Timer1

void setup() {
  Serial.begin(9600);
  // Đặt tốc độ cho cả 4 động cơ (0–255)
  uint8_t speed = 200;
  motorLeftFront.setSpeed(speed);
  motorRightFront.setSpeed(speed);
  motorLeftRear.setSpeed(speed);
  motorRightRear.setSpeed(speed);
}

void loop() {
  // Chạy tiến
  Serial.println("Chạy tiến");
  motorLeftFront.run(FORWARD);
  motorRightFront.run(FORWARD);
  // motorLeftRear.run(FORWARD);
  // motorRightRear.run(FORWARD);
//   delay(2000);

//   // Dừng
//   Serial.println("Dừng");
//   motorLeftFront.run(RELEASE);
//   motorRightFront.run(RELEASE);
//   motorLeftRear.run(RELEASE);
//   motorRightRear.run(RELEASE);
//   delay(1000);

//   // Chạy lùi
//   Serial.println("Chạy lùi");
//   motorLeftFront.run(BACKWARD);
//   motorRightFront.run(BACKWARD);
//   motorLeftRear.run(BACKWARD);
//   motorRightRear.run(BACKWARD);
//   delay(2000);

//   // Dừng
//   Serial.println("Dừng");
//   motorLeftFront.run(RELEASE);
//   motorRightFront.run(RELEASE);
//   motorLeftRear.run(RELEASE);
//   motorRightRear.run(RELEASE);
//   delay(1000);
}
