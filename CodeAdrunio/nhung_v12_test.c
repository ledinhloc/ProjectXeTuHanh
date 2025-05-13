#include <AFMotor.h>
#include <Servo.h>

AF_DCMotor motorLeftFront(  2, MOTOR12_1KHZ);  // M1, PWM Timer5
AF_DCMotor motorRightFront( 1, MOTOR12_1KHZ);  // M2, PWM Timer5
AF_DCMotor motorLeftRear(   3, MOTOR34_1KHZ);  // M3, PWM Timer1
AF_DCMotor motorRightRear(  4, MOTOR34_1KHZ);  // M4, PWM Timer1
// Cấu hình cảm biến dò line
// 5 cảm biến kết nối với các chân analog A0-A4
// blackThreshold: Ngưỡng để xác định vạch đen (giá trị analog)
const int sensorPins[5] = {A0, A1, A2, A3, A4};
const int blackThreshold = 422;

// Cấu hình cảm biến siêu âm để tránh vật cản
// trigPin: Chân phát tín hiệu
// echoPin: Chân nhận tín hiệu phản hồi
const int trigPin = 13;
const int echoPin = 11;
const int buzzerPin = 9;  // Chân kết nối còi
Servo myServo; // Servo để quay cảm biến siêu âm

// Các thông số điều chỉnh tốc độ và góc quay
const int baseSpeed = 90;        // Tốc độ cơ bản của động cơ
const float turnFactor = 1.2;     // Hệ số điều chỉnh khi rẽ
const int obstacleDistance = 25;  // Khoảng cách phát hiện vật cản (cm)
const int tocDoXoay = baseSpeed*1.5; // Tốc độ khi xoay

// Các biến điều khiển và trạng thái
char lastCommand = 'S';           // Lệnh cuối cùng
bool manualMode = false;          // Chế độ điều khiển (true: thủ công, false: tự động)
bool isInitialized = false;       // Kiểm tra đã khởi tạo chưa
unsigned long lastCommandTime = 0; // Thời điểm nhận lệnh cuối
const unsigned long COMMAND_TIMEOUT = 500;  // Thời gian chờ lệnh mới (ms)
char lastReceivedCommand = 'S';    // Lệnh nhận được cuối cùng
const unsigned long DEBOUNCE_DELAY = 50;    // Thời gian chống nhiễu (ms)
const unsigned long CONTROL_PERIOD = 5;     // 5 ms ~ 200 Hz control loop
unsigned long lastControlTime = 0;
unsigned long lastLoopTime = 0;    // Thời điểm thực hiện loop cuối
const unsigned long LOOP_PERIOD = 5;       // 20ms ~ 50Hz loop frequency

char incoming;  // Biến lưu lệnh nhận từ Serial
int command = 0; // Biến lưu lệnh điều khiển

// Thêm định nghĩa chân cảm biến ánh sáng và đèn LED
const int lightSensorPin = A5;    // Cảm biến ánh sáng ở A5
const int ledPin = 10;             // Đèn LED ở chân digital 5
const int lightThreshold = 500;   // Ngưỡng ánh sáng (có thể điều chỉnh)
const unsigned long LIGHT_CHECK_INTERVAL = 1000;  // Kiểm tra ánh sáng mỗi 1 giây
unsigned long lastLightCheck = 0;  // Thời điểm kiểm tra ánh sáng cuối cùng
char lastTurnDirection = 'L';      // Lưu hướng rẽ cuối cùng (L: trái, R: phải)

// Khai báo các hàm
void setup();
void loop();
void stopAllMotors();
void moveForward(int speed);
void moveBackward(int speed);
void turnLeft();
void turnRight();
void handleManualControl(char cmd);
void handleAutoMode();
void mainChayTheoLine();
float readDistance();
void scanSides(int distOut[3]);
void avoidObstacle();
void beepBuzzer();
void checkLight();

// Hàm khởi tạo
void setup() {
  Serial.begin(115200);  // Khởi tạo Serial với tốc độ 115200 baud
  Serial.println("Robot do line + tranh vat + dieu khien Bluetooth");
  
  // Cấu hình chân cảm biến siêu âm
  // pinMode(trigPin, OUTPUT);
  // pinMode(echoPin, INPUT);
  // pinMode(buzzerPin, OUTPUT);  // Cấu hình chân còi là OUTPUT
  
  // Khởi tạo chân LED
  pinMode(ledPin, OUTPUT);
  
  // Khởi tạo servo và đặt vị trí giữa
  myServo.attach(9);
  myServo.write(90);
  delay(500); 
}

// Hàm báo động bằng còi
void beepBuzzer() {
  digitalWrite(buzzerPin, HIGH);  // Bật còi
  delay(100);                     // Kêu 100ms
  digitalWrite(buzzerPin, LOW);   // Tắt còi
  delay(100);                     // Đợi 100ms
  digitalWrite(buzzerPin, HIGH);  // Bật còi lần 2
  delay(100);                     // Kêu 100ms
  digitalWrite(buzzerPin, LOW);   // Tắt còi
}

// Hàm chính chạy liên tục
void loop() {
  // checkLight();           // Kiểm tra ánh sáng và điều khiển đèn
  mainChayTheoLine();
  // unsigned long currentTime = millis();
  
  // // Kiểm tra thời gian giữa các lần thực hiện loop
  // if (currentTime - lastLoopTime < LOOP_PERIOD) {
  //   return;
  // }
  //   lastLoopTime = currentTime;
  //   mainChayTheoLine();
    
    // // Đọc lệnh từ Serial
    // if(Serial.available() > 0) {
    //   incoming = Serial.read();
      
    //   // Bỏ qua ký tự xuống dòng và nhiễu
    //   if (incoming == '\n' || incoming == '\r') return;

    //   // Xử lý lệnh chế độ
    //   switch(incoming) {
    //     case '1': 
    //       command = 1;  // Chuyển sang chế độ điều khiển bằng tay
    //       break;
    //     case '2': 
    //       command = 2;  // Chuyển sang chế độ tự động theo line
    //       break;
    //     case '0': 
    //       command = 0;  // Chuyển sang chế độ dừng
    //       stopAllMotors();
    //       break;
    //     default:
    //       lastCommandTime = currentTime;
    //       if(command == 1) {
    //         // Xử lý lệnh điều khiển trong chế độ thủ công
    //         handleManualControl(incoming);
    //       }
    //       break;
    //   }
    // }
    
    // // Xử lý chế độ tự động theo line
    // if(command == 2) {
    //   mainChayTheoLine();
    // }
  
}

// Hàm dừng tất cả động cơ
void stopAllMotors() {
  motorLeftFront.run(RELEASE);
  motorRightFront.run(RELEASE);
  motorLeftRear.run(RELEASE);
  motorRightRear.run(RELEASE);
}

// Hàm điều khiển xe tiến
void moveForward(int speed) {
  motorLeftFront.setSpeed(speed);
  motorRightFront.setSpeed(speed);
  motorLeftRear.setSpeed(speed);
  motorRightRear.setSpeed(speed);
  
  motorLeftFront.run(FORWARD);
  motorRightFront.run(FORWARD);
  motorLeftRear.run(FORWARD);
  motorRightRear.run(FORWARD);
}

// Hàm điều khiển xe lùi
void moveBackward(int speed) {
  motorLeftFront.setSpeed(speed);
  motorRightFront.setSpeed(speed);
  motorLeftRear.setSpeed(speed);
  motorRightRear.setSpeed(speed);
  
  motorLeftFront.run(BACKWARD);
  motorRightFront.run(BACKWARD);
  motorLeftRear.run(BACKWARD);
  motorRightRear.run(BACKWARD);
}

// Hàm điều khiển xe rẽ trái
void turnLeft() {
  motorLeftFront.setSpeed(tocDoXoay);
  motorRightFront.setSpeed(tocDoXoay);
  motorLeftRear.setSpeed(tocDoXoay);
  motorRightRear.setSpeed(tocDoXoay);
  
  motorLeftFront.run(BACKWARD);
  motorRightFront.run(FORWARD);
  motorLeftRear.run(BACKWARD);
  motorRightRear.run(FORWARD);
}

// Hàm điều khiển xe rẽ phải
void turnRight() {
  motorLeftFront.setSpeed(tocDoXoay);
  motorRightFront.setSpeed(tocDoXoay);
  motorLeftRear.setSpeed(tocDoXoay);
  motorRightRear.setSpeed(tocDoXoay);
  
  motorLeftFront.run(FORWARD);
  motorRightFront.run(BACKWARD);
  motorLeftRear.run(FORWARD);
  motorRightRear.run(BACKWARD);
}

// Hàm đọc khoảng cách từ cảm biến siêu âm
float readDistance() {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  long duration = pulseIn(echoPin, HIGH);
  return duration * 0.034 / 2;  // Tính khoảng cách (cm)
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

    // Serial.print("Scan ");
    // Serial.print(angles[i]);
    // Serial.print("° = ");
    // Serial.print(distOut[i]);
    // Serial.println(" cm");
  }
  myServo.write(90);  // trả servo về giữa
  delay(200);
}

// Hàm chính xử lý tránh chướng ngại
void avoidObstacle() {
  int dists[3];
  scanSides(dists);          // dists[0]=giữa, [1]=trái, [2]=phải
  int thoiGianQuay = 1000;
  int thoiGianQuayLai = 500;  // Thời gian quay lại line
  
  // Ưu tiên quay trái nếu trống
  if (dists[1] > obstacleDistance) {
    Serial.println("Quay TRÁI...");
    turnLeft();
    delay(thoiGianQuay);
    // Quay phải để quay lại line
    Serial.println("Quay lại line...");
    turnRight();
    delay(thoiGianQuayLai);
  }
  // Nếu trái chặn, thử phải
  else if (dists[2] > obstacleDistance) {
    Serial.println("Quay PHẢI...");
    turnRight();
    delay(thoiGianQuay);
    // Quay trái để quay lại line
    Serial.println("Quay lại line...");
    turnLeft();
    delay(thoiGianQuayLai);
  }
  // Cả hai bên đều chặn → lùi và scan lại
  else {
    Serial.println("Hai bên đều chặn, lùi lại...");
    moveBackward(tocDoXoay);
    delay(thoiGianQuay);
    // Có thể gọi đệ quy hoặc lập lại scan
    scanSides(dists);
    if (dists[1] > dists[2]) {
      Serial.println("Chọn quay TRÁI sau lùi");
      turnLeft();
      delay(thoiGianQuay);
      // Quay phải để quay lại line
      Serial.println("Quay lại line...");
      turnRight();
      delay(thoiGianQuayLai);
    } else {
      Serial.println("Chọn quay PHẢI sau lùi");
      turnRight();
      delay(thoiGianQuay);
      // Quay trái để quay lại line
      Serial.println("Quay lại line...");
      turnLeft();
      delay(thoiGianQuayLai);
    }
  }

  // Chạy thẳng cho đến khi tìm thấy line
  Serial.println("Chạy thẳng tìm line...");
  moveForward(baseSpeed);
  
  while(true) {
    // Đọc cảm biến line
    bool onBlack[5];
    int sensorSum = 0;
    for (int i = 0; i < 5; i++) {
      int raw = analogRead(sensorPins[i]);
      onBlack[i] = (raw < blackThreshold);
      if (onBlack[i]) {
        sensorSum++;
      }
    }
    
    // Nếu tìm thấy line (ít nhất 1 cảm biến đọc được vạch đen)
    if (sensorSum > 0) {
      Serial.println("Đã tìm thấy line!");
      return;  // Thoát hàm để quay lại chạy theo line
    }
    
    delay(50);  // Đợi một chút trước khi đọc lại
  }
}

// Hàm xử lý lệnh điều khiển thủ công
void handleManualControl(char cmd) {
  // Xử lý các lệnh điều khiển
  switch(cmd) {
    case 'F': 
      moveForward(baseSpeed); 
      break;  // Tiến với tốc độ cơ bản
    case 'B': 
      moveBackward(baseSpeed); 
      break; // Lùi với tốc độ cơ bản
    case 'L': 
      turnLeft(); 
      break;              // Rẽ trái với tốc độ xoay
    case 'R': 
      turnRight(); 
      break;             // Rẽ phải với tốc độ xoay
    case 'S': 
      stopAllMotors(); 
      break;         // Dừng
  }
}

// Hàm xử lý chế độ tự động theo line
void mainChayTheoLine() {
  unsigned long now = millis();

  // // Kiểm tra vật cản trước
  // int distance = readDistance();
  // if (distance < obstacleDistance) {
  //     stopAllMotors();
  //     Serial.println("VẬT CẢN TRƯỚC!");
  //     avoidObstacle();
  //     return;
  // }

  if (now - lastControlTime < CONTROL_PERIOD) {
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
    
    // Lưu hướng rẽ cuối cùng
    if (error > 0) {
      lastTurnDirection = 'L';
    } else if (error < 0) {
      lastTurnDirection = 'R';
    }
  }
  else {
    // Mất vạch: rẽ theo hướng cuối cùng đã lưu
    if (lastTurnDirection == 'L') {
      leftSpeed  = baseSpeed / 2;
      rightSpeed = baseSpeed + baseSpeed / 2;
    } else {
      leftSpeed  = baseSpeed + baseSpeed / 2;
      rightSpeed = baseSpeed / 2;
    }
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

// Thêm hàm kiểm tra ánh sáng và điều khiển đèn
void checkLight() {
  unsigned long currentTime = millis();
  
  // Chỉ kiểm tra ánh sáng sau mỗi khoảng thời gian định kỳ
  if (currentTime - lastLightCheck >= LIGHT_CHECK_INTERVAL) {
    int lightValue = analogRead(lightSensorPin);
    if (lightValue > lightThreshold) {  // Nếu toi
      digitalWrite(ledPin, HIGH);        // Tắt đèn
    } else {
      digitalWrite(ledPin, LOW);       // Bật đèn
    }
    lastLightCheck = currentTime;  // Cập nhật thời điểm kiểm tra cuối
  }
}