// Định nghĩa các chân điều khiển động cơ
#define MOTORLEFTSPEED 5    // Chân điều khiển tốc độ động cơ trái (PWM)
#define MOTORRIGHTSPEED 6   // Chân điều khiển tốc độ động cơ phải (PWM)

#define MOTORLEFTA 4        // Chân điều khiển chiều quay động cơ trái A
#define MOTORLEFTB 7        // Chân điều khiển chiều quay động cơ trái B
#define MOTORRIGHTA 2       // Chân điều khiển chiều quay động cơ phải A
#define MOTORRIGHTB 3       // Chân điều khiển chiều quay động cơ phải B

// Định nghĩa chân cảm biến siêu âm HC-SR04
#define TRIG A4            // Chân phát xung
#define ECHO A3            // Chân nhận xung

// Định nghĩa chân cảm biến dò line
#define SENSORLEFT A0      // Cảm biến line bên trái
#define SENSORCENTER A1    // Cảm biến line ở giữa
#define SENSORRIGHT A2     // Cảm biến line bên phải

// Định nghĩa chân cảm biến ánh sáng và đèn LED
#define LIGHTSENSOR A5     // Cảm biến ánh sáng
#define LIGHT1 11          // Đèn LED 1
#define LIGHT2 12          // Đèn LED 2

// Biến toàn cục
unsigned long CurrentDistance;  // Lưu khoảng cách hiện tại từ cảm biến siêu âm
byte LastLine=0;               // Lưu trạng thái line cuối cùng (0: trái, 1: phải)
unsigned long TimeCount;       // Biến đếm thời gian

// Hàm đo khoảng cách bằng cảm biến siêu âm
unsigned long CheckSensor()
{
   unsigned long duration;     // Biến đo thời gian
   unsigned long Distance;     // Biến lưu khoảng cách
   Distance=0;
   while(Distance<1)          // Đảm bảo đo được khoảng cách hợp lệ
   {
    digitalWrite(TRIG,LOW);    // Tắt chân trig
    delayMicroseconds(2);     // Đợi 2 micro giây
    digitalWrite(TRIG,HIGH);   // Phát xung từ chân trig
    delayMicroseconds(10);    // Xung có độ dài 10 micro giây
    digitalWrite(TRIG,LOW);    // Tắt chân trig
    duration = pulseIn(ECHO,HIGH);  // Đo độ rộng xung HIGH ở chân echo
    Distance = duration/2/29.412;    // Tính khoảng cách (cm)
   }
   Serial.println(Distance);   // In khoảng cách ra Serial Monitor
   return Distance;
}

// Hàm điều khiển xe theo line
void CheckLine()
{
  // Cập nhật trạng thái line cuối cùng
  if(digitalRead(SENSORLEFT)==HIGH) LastLine=0;
  else if(digitalRead(SENSORRIGHT)==HIGH) LastLine=1;

  // Điều khiển xe dựa vào trạng thái các cảm biến
  if(digitalRead(SENSORCENTER)==HIGH)  // Nếu phát hiện line ở giữa
  {
    MotorRightsetSpeed(150);           // Đặt tốc độ động cơ
    MotorLeftsetSpeed(150);
    RunForward();                      // Chạy thẳng
  }
  else if(digitalRead(SENSORLEFT)==HIGH&&digitalRead(SENSORCENTER)==LOW&&digitalRead(SENSORRIGHT)==LOW)  // Line ở bên trái
  {
    MotorRightsetSpeed(150);
    MotorLeftsetSpeed(150);
    MoveLeft();                        // Di chuyển sang trái
  }
  else if(digitalRead(SENSORLEFT)==LOW&&digitalRead(SENSORCENTER)==LOW&&digitalRead(SENSORRIGHT)==HIGH)  // Line ở bên phải
  {
    MotorRightsetSpeed(150);
    MotorLeftsetSpeed(150);
    MoveRight();                       // Di chuyển sang phải
  }
  else if(digitalRead(SENSORLEFT)==LOW&&digitalRead(SENSORCENTER)==LOW&&digitalRead(SENSORRIGHT)==LOW)  // Mất line
  {
    MotorRightsetSpeed(150);
    MotorLeftsetSpeed(150);
    if(LastLine==0) TurnLeft();        // Rẽ trái nếu line cuối ở bên trái
    else TurnRight();                  // Rẽ phải nếu line cuối ở bên phải
  }
  else if(digitalRead(SENSORLEFT)==HIGH&&digitalRead(SENSORCENTER)==HIGH&&digitalRead(SENSORRIGHT)==HIGH)  // Cả 3 cảm biến đều phát hiện
  {
    Stop();                            // Dừng xe
  }
}

// Hàm điều chỉnh tốc độ động cơ phải
void MotorRightsetSpeed(byte Speed)
{
  analogWrite(MOTORRIGHTSPEED,Speed);
}

// Hàm điều chỉnh tốc độ động cơ trái
void MotorLeftsetSpeed(byte Speed)
{
  analogWrite(MOTORLEFTSPEED,Speed);
}

// Hàm điều khiển xe chạy thẳng
void RunForward()
{
 digitalWrite(MOTORRIGHTA,HIGH);
 digitalWrite(MOTORRIGHTB,LOW);
 digitalWrite(MOTORLEFTA,HIGH);
 digitalWrite(MOTORLEFTB,LOW);
}

// Hàm điều khiển xe chạy lùi
void RunBackward()
{
 digitalWrite(MOTORRIGHTA,LOW);
 digitalWrite(MOTORRIGHTB,HIGH);
 digitalWrite(MOTORLEFTA,LOW);
 digitalWrite(MOTORLEFTB,HIGH);
}

// Hàm điều khiển xe rẽ phải
void TurnRight()
{
 digitalWrite(MOTORRIGHTA,HIGH);
 digitalWrite(MOTORRIGHTB,LOW);
 digitalWrite(MOTORLEFTA,LOW);
 digitalWrite(MOTORLEFTB,HIGH);
}

// Hàm điều khiển xe rẽ trái
void TurnLeft()
{
 digitalWrite(MOTORRIGHTA,LOW);
 digitalWrite(MOTORRIGHTB,HIGH);
 digitalWrite(MOTORLEFTA,HIGH);
 digitalWrite(MOTORLEFTB,LOW);
}

// Hàm điều khiển xe di chuyển sang phải
void MoveRight()
{
 digitalWrite(MOTORRIGHTA,HIGH);
 digitalWrite(MOTORRIGHTB,LOW);
 digitalWrite(MOTORLEFTA,LOW);
 digitalWrite(MOTORLEFTB,LOW);
}

// Hàm điều khiển xe di chuyển sang trái
void MoveLeft()
{
 digitalWrite(MOTORRIGHTA,LOW);
 digitalWrite(MOTORRIGHTB,LOW);
 digitalWrite(MOTORLEFTA,HIGH);
 digitalWrite(MOTORLEFTB,LOW);
}

// Hàm dừng xe
void Stop()
{
 digitalWrite(MOTORRIGHTA,LOW);
 digitalWrite(MOTORRIGHTB,LOW);
 digitalWrite(MOTORLEFTA,LOW);
 digitalWrite(MOTORLEFTB,LOW);
}

// Hàm kiểm tra và điều khiển đèn LED
void CheckLight()
{
  if(digitalRead(LIGHTSENSOR)==LOW)  // Nếu trời tối
  {
    digitalWrite(LIGHT1,LOW);        // Tắt đèn
    digitalWrite(LIGHT2,LOW);
  }
  else                               // Nếu trời sáng
  {
    digitalWrite(LIGHT1,HIGH);       // Bật đèn
    digitalWrite(LIGHT2,HIGH);
  }
}

// Hàm khởi tạo
void setup() {
  // Khởi tạo các chân điều khiển động cơ
  pinMode(MOTORLEFTSPEED,OUTPUT);
  pinMode(MOTORRIGHTSPEED,OUTPUT);
  pinMode(MOTORLEFTA,OUTPUT);
  pinMode(MOTORLEFTB,OUTPUT);
  pinMode(MOTORRIGHTA,OUTPUT);
  pinMode(MOTORRIGHTB,OUTPUT);

  // Khởi tạo các chân cảm biến và đèn LED
  pinMode(LIGHTSENSOR,INPUT);
  pinMode(LIGHT1,OUTPUT);
  pinMode(LIGHT2,OUTPUT);
  pinMode(TRIG,OUTPUT);
  pinMode(ECHO,INPUT);
  pinMode(SENSORLEFT,INPUT);
  pinMode(SENSORCENTER,INPUT);
  pinMode(SENSORRIGHT,INPUT);

  Serial.begin(9600);               // Khởi tạo Serial để debug
  
  TimeCount=millis();               // Khởi tạo biến đếm thời gian
  MotorRightsetSpeed(255);          // Đặt tốc độ động cơ ban đầu
  MotorLeftsetSpeed(255);

  Serial.println("START");          // Thông báo khởi động
}

// Hàm chính chạy liên tục
void loop() {
  CheckLine();                      // Kiểm tra và điều khiển theo line

  if(millis()-TimeCount>50)         // Mỗi 50ms thực hiện một lần
  {
    CheckLight();                   // Kiểm tra ánh sáng
    CurrentDistance=CheckSensor();  // Đo khoảng cách
    
    if(CurrentDistance<25)          // Nếu phát hiện vật cản (khoảng cách < 25cm)
    {
      // Chuỗi động tác tránh vật cản
      Stop();
      delay(1000);
      TurnLeft();
      delay(400);
      Stop();
      delay(1000);
      RunForward();
      delay(500);
      Stop();
      delay(1000);
      TurnRight(); 
      delay(300);
      Stop();
      delay(1000);
      RunForward();
      delay(4200);
      Stop();
      delay(1000);
      TurnRight();
      delay(400);
      Stop();
      delay(100);
      RunForward();
      
      // Giảm tốc độ để tìm lại line
      MotorRightsetSpeed(120);
      MotorLeftsetSpeed(120);
      while(digitalRead(SENSORLEFT)==LOW);  // Đợi cho đến khi tìm thấy line
      delay(300);
      Stop();
      
      // Tăng tốc độ và tiếp tục di chuyển
      MotorRightsetSpeed(170);
      MotorLeftsetSpeed(170);
      delay(1000);
      TurnLeft();
      while(digitalRead(SENSORLEFT)==LOW&&digitalRead(SENSORCENTER)==LOW&&digitalRead(SENSORRIGHT)==LOW);  // Đợi cho đến khi tìm thấy line
    }
    TimeCount=millis();  // Cập nhật thời gian
  }  
}
