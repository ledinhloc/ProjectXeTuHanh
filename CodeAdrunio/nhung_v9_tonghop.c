#include <AFMotor.h>
#include <Servo.h>

// Khai báo 4 động cơ DC sử dụng driver AFMotor
// Mỗi động cơ được kết nối với các chân 1,2,3,4 của driver
// MOTOR12_64KHZ: Tần số PWM để điều khiển tốc độ động cơ
AF_DCMotor motorLeftFront(1, MOTOR12_64KHZ);
AF_DCMotor motorRightFront(2, MOTOR12_64KHZ);
AF_DCMotor motorLeftRear(3, MOTOR12_64KHZ);
AF_DCMotor motorRightRear(4, MOTOR12_64KHZ);

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
Servo myServo; // Servo để quay cảm biến siêu âm

// Các thông số điều chỉnh tốc độ và góc quay
const int baseSpeed = 100;        // Tốc độ cơ bản của động cơ
const float turnFactor = 1.8;     // Hệ số điều chỉnh khi rẽ
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

// Hàm khởi tạo
void setup() {
  Serial.begin(115200);  // Khởi tạo Serial với tốc độ 115200 baud
  Serial.println("Robot do line + tranh vat + dieu khien Bluetooth");
  
  // Cấu hình chân cảm biến siêu âm
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  
  // Khởi tạo servo và đặt vị trí giữa
  myServo.attach(9);
  myServo.write(90);
  delay(2000);  // Đợi 2 giây để servo ổn định
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

// Hàm xử lý lệnh điều khiển thủ công
void handleManualControl(char cmd) {
  // Kiểm tra vật cản (đã comment)
  // float distance = readDistance();
  // if(distance < obstacleDistance && (cmd == 'F' || cmd == 'L' || cmd == 'R')) {
  //   stopAllMotors();
  //   Serial.println("OBSTACLE!");
  //   return;
  // }

  // Xử lý các lệnh điều khiển
  switch(cmd) {
    case 'F': moveForward(baseSpeed); break;  // Tiến
    case 'B': moveBackward(baseSpeed); break; // Lùi
    case 'L': turnLeft(); break;              // Rẽ trái
    case 'R': turnRight(); break;             // Rẽ phải
    case 'S': stopAllMotors(); break;         // Dừng
  }
  delay(500);  // Đợi 500ms trước khi thực hiện lệnh tiếp theo
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
    moveBackward(baseSpeed);
    delay(200);
    // Có thể gọi đệ quy hoặc lập lại scan
    scanSides(dists);
    if (dists[1] > dists[2]) {
      Serial.println("Chọn quay TRÁI sau lùi");
      turnLeft()
    } else {
      Serial.println("Chọn quay PHẢI sau lùi");
      turnRight();
    }
  }
}

const unsigned long CONTROL_PERIOD = 5;  // 5 ms ~ 200 Hz control loop
unsigned long lastControlTime = 0;
// Hàm xử lý chế độ tự động theo line
void mainChayTheoLine(){
  unsigned long now = millis();
  if (now - lastControlTime < CONTROL_PERIOD) {
    // Ở những lúc không đến kỳ, ta có thể làm việc khác (nếu có)
    // Nếu có vật cản quá gần → thì xoay trái, xoay phải nếu mà trống bên nào thì chạy bên đó, nếu bị chặn hết thì lui lại và tìm
    int distance = readDistance();
    if (distance < obstacleDistance) {
      stopAllMotors();
      Serial.println("VẬT CẢN TRƯỚC!");
      delay(200);
      avoidObstacle();
      return;
    }
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
    leftSpeed  = baseSpeed / 3;
    rightSpeed = baseSpeed*2;
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


// Biến toàn cục cho chế độ và lệnh
int command = 0;  // 0: dừng, 1: điều khiển bằng tay, 2: tự động theo line
char incoming;    // Lưu lệnh nhận được

// Hàm chính chạy liên tục
void loop() {
  // Đọc lệnh từ Serial
  if(Serial.available() > 0) {
    incoming = Serial.read();
    
    // Bỏ qua ký tự xuống dòng và nhiễu
    if (incoming == '\n' || incoming == '\r') return;

    // Xử lý lệnh chế độ
    switch(incoming) {
      case '1': 
        command = 1;  // Chuyển sang chế độ điều khiển bằng tay
        break;
      case '2': 
        command = 2;  // Chuyển sang chế độ tự động theo line
        break;
      case '0': 
        command = 0;  // Chuyển sang chế độ dừng
        break;
      default:
        lastCommandTime = millis();
        if(command == 1) {
          // Xử lý lệnh điều khiển trong chế độ thủ công
          handleManualControl(incoming);
        } else if(command == 2) {
          // Xử lý chế độ tự động theo line
          mainChayTheoLine();
        } else if(command == 0) {
          // Dừng xe trong chế độ dừng
          stopAllMotors();
        }
        break;
    }
  }
  
  // Tự động dừng sau timeout
  if (millis() - lastCommandTime > COMMAND_TIMEOUT) {
    stopAllMotors();
    lastReceivedCommand = 'S';
  }
  delay(50);  // Đợi 50ms trước vòng lặp tiếp theo
}