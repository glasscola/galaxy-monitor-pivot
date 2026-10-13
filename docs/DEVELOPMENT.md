# 개발 및 검증 안내

## 구성

| 파일 | 역할 |
| --- | --- |
| `MirrorActivity.java` | 기본 화면, 방향 선택, 화면 공유 승인 요청 |
| `MirrorService.java` | MediaProjection 캡처, 외부 Presentation 출력, 알림 직접 제어 |
| `MirrorGeometry.java` | 회전·비율 유지·화면 채우기 좌표 계산 |
| `MainActivity.java` / `PivotService.java` | 선택적인 Shizuku 시스템 회전 모드 |
| `res/layout/notification_pivot*.xml` | 작은 알림 / 펼친 알림의 직접 제어 버튼 |
| `tests/` | 외부 시스템 I/O를 대체한 호스트 검증 |

화면 공유 승인을 받은 뒤 포그라운드 서비스에서 휴대폰 화면을 캡처합니다. 외부 화면의 TextureView에 회전과 크기 변환을 적용합니다. 알림의 방향 버튼은 `PendingIntent.getService`로 실행 중인 서비스에 바로 명령을 전달하므로 앱 Activity를 열지 않습니다.

## 빌드

Windows PowerShell 7+, JDK 17, Android SDK Platform 36 / Build Tools 36.0.0이 필요합니다.

```powershell
.\build-apk.ps1 -SdkPath 'C:\path\to\Android\Sdk'
```

Android Studio용 Gradle 프로젝트도 포함합니다. Gradle과 Android Gradle Plugin을 처음 받으려면 네트워크 연결이 필요합니다. Shizuku API 13.1.5와 AndroidX annotation 1.3.0은 `libs/`에 포함되어 있습니다.

직접 관리하는 서명 키를 사용하는 예:

```powershell
$env:PIVOT_STORE_PASSWORD = '자신의 키 비밀번호'
.\build-apk.ps1 -SdkPath 'C:\path\to\Android\Sdk' -KeyStorePath 'C:\private\release.jks' -KeyAlias 'release'
```

배포 APK의 서명 키는 공개 저장소에 포함하지 않습니다. 별도 키로 만든 APK는 기존 설치와 서명 정체성이 달라집니다.

## 검증 범위

```powershell
.\test-host.ps1
```

- 시스템 회전 로직 18개 검사: 기본 화면과 부적절한 디스플레이 거부, 고유 정보 확인, 설정 보관, 회전 확인, 부분 실패 및 20초 타이머 복원 등
- 복제 좌표 계산 11개 검사: 세로/가로/반대 방향, 비율 유지, 화면 채우기, 해상도 제한 등
- APK 빌드와 서명 검증, 알림 리소스 포함 확인
- 배포용 화면 자료는 Android 13 에뮬레이터에서 실제 APK를 실행해 얻습니다. 가상 외부 화면으로 앱 실행과 출력 흐름을 확인하는 것이며, 실제 USB-C 하드웨어나 삼성 펌웨어의 호환성 시험은 아닙니다.

실행 확인:

| 항목 | 결과 |
| --- | --- |
| 화면 공유 승인 후 복제 서비스 시작 | 포그라운드 서비스 실행 확인 |
| 1920 × 1080 가상 외부 모니터 출력 | 세로 방향으로 회전한 실제 출력 프레임 캡처 |
| 작은 알림 / 펼친 알림 | 실제 Android System UI에서 버튼 표시 확인 |
| 앱 백그라운드 상태에서 알림의 가로 버튼 선택 | 알림의 가로 체크 표시 반영; 전면 Activity는 홈 런처 유지 |
| 앱으로 복귀 | 가로 버튼과 미리보기가 가로 선택 상태로 표시 |

문서 캡처용 가상 모니터 생성 도구는 배포 소스와 APK에 포함하지 않습니다.

갤럭시 S20의 1.1 회전 기능 동작은 사용자 확인에 근거합니다. 테스트 결과를 모든 기종에 일반화하지 않습니다.

## 개인정보

앱은 인터넷 권한을 선언하지 않습니다. 기본 모드의 화면은 파일에 저장하지 않고 외부 화면 표시에만 사용합니다. 전체 화면 공유에는 알림과 개인정보가 보일 수 있습니다. Android의 화면 공유 승인과 서비스 알림을 통해 세션을 시작하고 종료합니다.
