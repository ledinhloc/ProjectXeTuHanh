#include <AFMotor.h>
#include <Servo.h>

// Khai báo động cơ
AF_DCMotor motorLeftFront(1, MOTOR12_64KHZ);
AF_DCMotor motorRightFront(2, MOTOR12_64KHZ);
AF_DCMotor motorLeftRear(3, MOTOR12_64KHZ);
AF_DCMotor motorRightRear(4, MOTOR12_64KHZ);

// Cảm biến dò line
const int sensorPins[5] = {A0, A1, A2, A3, A4};
const int blackThreshold = 422;

// Cảm biến siêu âm
const int trigPin = 13;
const int echoPin = 11;
Servo myServo;  // Servo gắn thêm nếu cần quay đầu dò

// Điều chỉnh tốc độ
const int baseSpeed = 58;
const float turnFactor = 1.8;
const int obstacleDistance = 25; // cm
const int tocDoXoay = baseSpeed*1.3;

void setup() {
  Serial.begin(9600);
  Serial.println("Robot do line + tranh vat");

  // Khai báo chân cảm biến siêu âm
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  // Khởi tạo servo
  myServo.attach(9);  // Gắn servo vào chân số 9
  myServo.write(90);  // Đặt servo ở góc 90 độ ban đầu
  delay(500);
}

const unsigned long CONTROL_PERIOD = 5;  // 5 ms ~ 200 Hz control loop
unsigned long lastControlTime = 0;

void loop() {
  unsigned long now = millis();
  if (now - lastControlTime < CONTROL_PERIOD) {
    // Ở những lúc không đến kỳ, ta có thể làm việc khác (nếu có)
    return;
  }
  lastControlTime = now;

  // 1) Đọc cảm biến line
  bool onBlack[5];
  int sensorSum = 0, weightedSum = 0;
  for (int i = 0; i < 5; i++) {
    int raw = analogRead(sensorPins[i]);
    onBlack[i] = (raw < blackThreshold);
    if (onBlack[i]) {
      sensorSum++;
      weightedSum += (2 - i);
    }
  }

  // 2) Tính PID/PD đơn giản (ở đây dùng tỷ lệ P)
  int leftSpeed  = baseSpeed;
  int rightSpeed = baseSpeed;
  if (sensorSum > 0) {
    float error = float(weightedSum) / sensorSum;
    float correction = error * turnFactor * baseSpeed;
    // Lệch trái error>0 → giảm tốc trái, tăng phải
    leftSpeed  = constrain(baseSpeed  - correction, 0, 255);
    rightSpeed = constrain(baseSpeed  + correction, 0, 255);
  }
  else {
    // Mất vạch hoàn toàn: ví dụ quay tại chỗ tìm lại
    leftSpeed  = baseSpeed / 2;
    rightSpeed = baseSpeed + baseSpeed / 2;
  }

  // 3) Cập nhật motor
  motorLeftFront.setSpeed(leftSpeed);
  motorLeftRear .setSpeed(leftSpeed);
  motorRightFront.setSpeed(rightSpeed);
  motorRightRear .setSpeed(rightSpeed);

  motorLeftFront.run(FORWARD);
  motorLeftRear.run(FORWARD);
  motorRightFront.run(FORWARD);
  motorRightRear.run(FORWARD);
}

// void loop() {
//   // Đo khoảng cách từ cảm biến siêu âm
//   long duration;
//   int distance;

//   digitalWrite(trigPin, LOW);
//   delayMicroseconds(2);
//   digitalWrite(trigPin, HIGH);
//   delayMicroseconds(10);
//   digitalWrite(trigPin, LOW);

//   // duration = pulseIn(echoPin, HIGH, 30000); // timeout 30ms = ~5m
//   // if (duration == 0) {
//   //   distance = 999; // nếu không đọc được, gán giá trị lớn
//   // } else {
//   //   distance = duration * 0.034 / 2;
//   // }

//   // Serial.print("Distance: ");
//   // Serial.print(distance);
//   // Serial.println(" cm");

  // // Nếu có vật cản quá gần → thì xoay trái, xoay phải nếu mà trống bên nào thì chạy bên đó, nếu bị chặn hết thì lui lại và tìm
  // if (distance < obstacleDistance) {
  //   stopAll();
  //   Serial.println("VẬT CẢN TRƯỚC!");
  //   delay(200);
  //   avoidObstacle();
  //   return;
  // }

//   // Đọc cảm biến line
//   bool onBlack[5];
//   int sensorSum = 0;
//   int weightedSum = 0;

//   for (int i = 0; i < 5; i++) {
//     int value = analogRead(sensorPins[i]);
//     onBlack[i] = (value < blackThreshold);
//     Serial.print(onBlack[i]);
//     Serial.print(" ");
//     if (onBlack[i]) {
//       sensorSum += 1;
//       weightedSum += (2 - i);  // Trọng số: giữa = 0, lệch trái/phải = ±1, ±2
//     }
//   }
//   Serial.println();

//   int leftSpeed = baseSpeed;
//   int rightSpeed = baseSpeed;



// // sensorSum : tổng cảm biến thấy vạch
// //weightedSum: tổng trọng số của các cảm biến thấy vạch
// //error : sai số bình quân vị trí vạch so với tâm robot
//   if (sensorSum > 0) {
//     // error>0; dang lech ben trai
//     //error <0: dang lech ben phải
//     float error = (float)weightedSum / sensorSum;
//     leftSpeed -= error * turnFactor * baseSpeed;
//     rightSpeed += error * turnFactor * baseSpeed;

//     leftSpeed = constrain(leftSpeed, 0, 255);
//     rightSpeed = constrain(rightSpeed, 0, 255);
//   } else {
//     // // Mất vạch hoàn toàn → dừng
//     // stopAll();
//     // Serial.println("MẤT VẠCH!");
//     // delay(100);
//     // return;
//   }

//   // Gán tốc độ cho động cơ
//   motorLeftFront.setSpeed(leftSpeed);
//   motorLeftRear.setSpeed(leftSpeed);
//   motorRightFront.setSpeed(rightSpeed);
//   motorRightRear.setSpeed(rightSpeed);

//   // Cho chạy tiến
//   motorLeftFront.run(FORWARD);
//   motorLeftRear.run(FORWARD);
//   motorRightFront.run(FORWARD);
//   motorRightRear.run(FORWARD);

//   delay(3);
// }

// Hàm dừng tất cả động cơ
void stopAll() {
  motorLeftFront.run(RELEASE);
  motorLeftRear.run(RELEASE);
  motorRightFront.run(RELEASE);
  motorRightRear.run(RELEASE);
}

// Quét trái – giữa – phải, trả về mảng 3 khoảng cách tương ứng
void scanSides(int distOut[3]) {
  const int angles[3] = {90, 45, 135};  // 90°: thẳng, 45°: trái, 135°: phải
  for (int i = 0; i < 3; i++) {
    myServo.write(angles[i]);
    delay(300);  // chờ servo xoay ổn định

    // đo khoảng cách
    digitalWrite(trigPin, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin, LOW);

    long dur = pulseIn(echoPin, HIGH, 30000);
    if (dur == 0) distOut[i] = 999;
    else distOut[i] = dur * 0.034 / 2;

    Serial.print("Scan ");
    Serial.print(angles[i]);
    Serial.print("° = ");
    Serial.print(distOut[i]);
    Serial.println(" cm");
  }
  myServo.write(90);  // trả servo về giữa
  delay(200);
}

// Lùi lại một chút
void goBackward(int ms) {
  motorLeftFront.setSpeed(tocDoXoay);
  motorLeftRear.setSpeed(tocDoXoay);
  motorRightFront.setSpeed(tocDoXoay);
  motorRightRear.setSpeed(tocDoXoay);
  motorLeftFront.run(BACKWARD);
  motorLeftRear.run(BACKWARD);
  motorRightFront.run(BACKWARD);
  motorRightRear.run(BACKWARD);
  delay(ms);
  stopAll();
}

// Hàm chính xử lý tránh chướng ngại
void avoidObstacle() {
  int dists[3];
  scanSides(dists);          // dists[0]=giữa, [1]=trái, [2]=phải
  int thoiGianQuay = 1000;
  // Ưu tiên quay trái nếu trống
  if (dists[1] > obstacleDistance) {
    Serial.println("Quay TRÁI...");
    pivotLeftInPlace(thoiGianQuay);
  }
  // Nếu trái chặn, thử phải
  else if (dists[2] > obstacleDistance) {
    Serial.println("Quay PHẢI...");
    pivotRightInPlace(thoiGianQuay);
  }
  // Cả hai bên đều chặn → lùi và scan lại
  else {
    Serial.println("Hai bên đều chặn, lùi lại...");
    goBackward(thoiGianQuay);
    delay(200);
    // Có thể gọi đệ quy hoặc lập lại scan
    scanSides(dists);
    if (dists[1] > dists[2]) {
      Serial.println("Chọn quay TRÁI sau lùi");
      pivotLeftInPlace(thoiGianQuay);
    } else {
      Serial.println("Chọn quay PHẢI sau lùi");
      pivotRightInPlace(thoiGianQuay);
    }
  }
}

//quay phai tai cho
void pivotRightInPlace(int ms) {
  // Cùng tốc độ cho cả 4 động cơ
  motorLeftFront.setSpeed(tocDoXoay);
  motorLeftRear.setSpeed(tocDoXoay);
  motorRightFront.setSpeed(tocDoXoay);
  motorRightRear.setSpeed(tocDoXoay);

  // Bánh trái tiến, bánh phải lùi để xoay chính giữa
  motorLeftFront.run(FORWARD);
  motorLeftRear.run(FORWARD);
  motorRightFront.run(BACKWARD);
  motorRightRear.run(BACKWARD);

  delay(ms);
  stopAll();
}

void pivotLeftInPlace(int ms) {
  // Cùng tốc độ cho cả 4 động cơ
  motorLeftFront.setSpeed(tocDoXoay);
  motorLeftRear.setSpeed(tocDoXoay);
  motorRightFront.setSpeed(tocDoXoay);
  motorRightRear.setSpeed(tocDoXoay);

  // Bánh trái chạy lùi, bánh phải chạy tiến
  motorLeftFront.run(BACKWARD);
  motorLeftRear.run(BACKWARD);
  motorRightFront.run(FORWARD);
  motorRightRear.run(FORWARD);

  delay(ms);
  stopAll();
}

