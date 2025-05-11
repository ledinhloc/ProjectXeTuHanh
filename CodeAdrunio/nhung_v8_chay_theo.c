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
Servo myServo;

// Điều chỉnh tốc độ
const int baseSpeed = 100;
const float turnFactor = 1.8;
const int obstacleDistance = 25; // cm
const int tocDoXoay = baseSpeed*1.5;

// Biến điều khiển
char lastCommand = 'S';
bool manualMode = false;
unsigned long lastCommandTime = 0;
const unsigned long COMMAND_TIMEOUT = 10000; // 1 giây không lệnh thì về auto
char lastReceivedCommand = 'S'; // Lưu lệnh cuối cùng
const unsigned long DEBOUNCE_DELAY = 50; // Chống nhiễu

void setup() {
  Serial.begin(115200);
  Serial.println("Robot do line + tranh vat + dieu khien Bluetooth");
  
  // pinMode(trigPin, OUTPUT);
  // pinMode(echoPin, INPUT);
  
  // myServo.attach(9);
  // myServo.write(90);
  delay(2000);
}

float readDistance() {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  long duration = pulseIn(echoPin, HIGH);
  return duration * 0.034 / 2;
}

void stopAllMotors() {
  motorLeftFront.run(RELEASE);
  motorRightFront.run(RELEASE);
  motorLeftRear.run(RELEASE);
  motorRightRear.run(RELEASE);
}

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

void handleManualControl(char cmd) {
//   float distance = readDistance();
  
//   if(distance < obstacleDistance && (cmd == 'F' || cmd == 'L' || cmd == 'R')) {
//     stopAllMotors();
//     Serial.println("OBSTACLE!");
//     return;
//   }

  switch(cmd) {
    case 'F': moveForward(baseSpeed); break;
    case 'B': moveBackward(baseSpeed); break;
    case 'L': turnLeft(); break;
    case 'R': turnRight(); break;
    case 'S': stopAllMotors(); break;
  }
}

void handleAutoMode() {
  // Đọc cảm biến line
  bool onBlack[5];
  int sensorSum = 0, weightedSum = 0;
  for(int i=0; i<5; i++) {
    int raw = analogRead(sensorPins[i]);
    onBlack[i] = (raw < blackThreshold);
    if(onBlack[i]) {
      sensorSum++;
      weightedSum += (2 - i);
    }
  }

  // Kiểm tra vật cản
  float distance = readDistance();
  if(distance < obstacleDistance) {
    stopAllMotors();
    delay(100);
    moveBackward(baseSpeed);
    delay(300);
    turnRight();
    delay(400);
    return;
  }

  // Điều khiển động cơ
  if(sensorSum > 0) {
    float error = float(weightedSum)/sensorSum;
    float correction = error * turnFactor * baseSpeed;
    int leftSpeed = constrain(baseSpeed - correction, 0, 255);
    int rightSpeed = constrain(baseSpeed + correction, 0, 255);
    
    motorLeftFront.setSpeed(leftSpeed);
    motorLeftRear.setSpeed(leftSpeed);
    motorRightFront.setSpeed(rightSpeed);
    motorRightRear.setSpeed(rightSpeed);
    
    motorLeftFront.run(FORWARD);
    motorLeftRear.run(FORWARD);
    motorRightFront.run(FORWARD);
    motorRightRear.run(FORWARD);
  } else {
    // Mất line - quay tại chỗ tìm line
    turnRight();
  }
}

//khi kí tự là "1" thì chạy theo người
void mainChayTheoNguoi(){

}
// khi kí tự là "2" thì chạy theo line
void mainChayTheoLine(){

}

// khi kí tự là "3" thì dừng lại đến khi nhận 1 hoặc 2
void mainDung(){
  stopAllMotors();
}


void loop() {
  // Đọc lệnh từ Serial
  if(Serial.available() > 0) {
    char incoming = Serial.read();
    // manualMode = true;
     // Bỏ qua ký tự xuống dòng và nhiễu
    if (incoming == '\n' || incoming == '\r') return;

    // Chỉ xử lý nếu lệnh mới khác lệnh cũ
    if (incoming != lastReceivedCommand) {
        lastReceivedCommand = incoming;
        lastCommandTime = millis();
        handleManualControl(incoming);
    }
  }

  // Tự động dừng sau timeout
    if (millis() - lastCommandTime > COMMAND_TIMEOUT) {
        stopAllMotors();
        lastReceivedCommand = 'S';
    }
  delay(50);
}