# 개발 및 검증 안내

## 구현 구조

| 파일 | 역할 |
| --- | --- |
| MirrorActivity | 출력·영역·배치·화질 UI, 권한 안내, 공유 승인, 설정 저장 |
| MirrorService | MediaProjection 세션, 외부 Presentation, 알림 액션, 회전 대응 |
| StreamRenderer | SurfaceTexture 입력, OpenGL ES 자르기·회전·합성·보정, 일시정지 프레임 보관 |
| RegionOverlay | 다른 앱 위에서 영역 편집, 테두리만 표시, 터치 통과 |
| RegionEntryActivity | 알림을 닫고 사용 중인 앱 위에 편집 도구 표시 |
| Scene / SceneStore | 정규화 좌표, 배치·비율 계산, JSON 설정·프리셋 |
| SceneCanvas / Ui | 앱 내부 영역/배치 도식, 공통 글자·버튼·카드 |
| MainActivity / PivotService | 기존 선택형 Shizuku 시스템 회전 방식 |

화면 흐름은 MediaProjection → SurfaceTexture → OpenGL ES → 외부 Presentation입니다. 하나의 입력 프레임에서 모든 영역을 잘라 같은 출력 프레임에 합성합니다. PNG/JPEG 저장이나 인코딩·네트워크 전송 과정은 없습니다.

입력은 기본적으로 현재 휴대폰 해상도를 유지합니다. 성능 모드는 긴 변을 1920픽셀로 제한합니다. 모니터 신호 해상도와 렌더링 Surface 크기는 별도로 표시합니다. 프레임 설정은 30/60fps 목표이며 실측 성능 보장은 아닙니다.

편집 중에는 마지막 합성 결과를 GPU framebuffer에 보관합니다. 입력 프레임은 계속 소비하므로 편집 도구가 들어간 오래된 입력이 쌓이지 않도록 합니다. 적용 후 오버레이 변경을 기다린 다음 실시간 출력으로 돌아옵니다.

일반 표시 모드에서는 안내선을 선택 영역 합집합 바깥쪽에만 그립니다. 샘플링은 선택 영역 안으로 제한합니다. 모니터에는 선이 포함되지 않도록 설계했으며, 화면 끝/겹친 영역에서는 안내선 일부가 생략될 수 있습니다. FLAG_SECURE로 아래 화면이 복원된다고 가정하지 않습니다.

터치 통과 모드는 FLAG_NOT_TOUCHABLE과 허용 범위 내의 Window alpha를 사용합니다. 안드로이드/One UI 또는 대상 앱의 오버레이 제한을 우회하지 않습니다. 별도 접근성 서비스나 루팅을 요구하지 않습니다.

## 빌드

PowerShell 7+, JDK 17, Android SDK Platform 36 / Build Tools 36.0.0:

~~~powershell
.\build-apk.ps1 -SdkPath 'C:\path\to\Android\Sdk'
.\test-host.ps1
~~~

Android Studio용 Gradle 프로젝트도 포함합니다. Shizuku API와 AndroidX annotation은 libs에 동봉되어 있습니다.

배포 키를 지정하려면 환경변수 PIVOT_STORE_PASSWORD와 -KeyStorePath, -KeyAlias를 사용하세요. 저장소에는 서명 키를 포함하지 않습니다. 별도 키로 만든 APK는 기존 배포 버전 위에 업데이트할 수 없습니다.

## v2.0 검증

검증 환경: Android 13 / API 33 에뮬레이터, 1080 × 2400 휴대폰 화면, 1920 × 1080 가상 모니터, 로컬 WebView 테스트 앱.

- 호스트 테스트: 기존 시스템 회전 18개, 회전 기하 11개, 새 영역·배치 기하 8,016개 검사 통과. 새 검사는 고정 시드 2,000개 입력으로 비율 고정·좌표 경계·출력 범위를 확인하는 반복 검사를 포함합니다.
- APK 컴파일, 리소스 패키징, v3 서명 검증 통과.
- 이전 배포 APK와 같은 서명 인증서 사용, 에뮬레이터에서 업데이트 설치 성공.
- 알림에서 방향 변경, 테두리 편집 진입 시 기존 앱 유지.
- 자유/모니터 비율 영역 편집과 적용, 도구·음영 제거 후 선만 표시.
- 다른 패키지의 WebView 버튼 클릭 및 스크롤 통과 확인.
- 웹스크롤에 따라 모니터의 선택 영역이 실시간으로 바뀜.
- 모니터 프레임에서 안내선과 도구가 제외된 결과 확인.
- 두 영역 합성, 최대 네 영역, 삭제, 좌우·위아래·자유 배치, 드래그와 숫자 조절 확인.
- 숫자 입력이 화면 범위를 넘으면 적용하지 않음.
- 설정 이름 저장과 불러오기 확인.
- 고화질 1080 × 2400과 성능 모드 864 × 1920 사이 실제 입력 크기 변경 확인.
- 30/60fps 목표 선택, 130% 글자 크기에서 줄바꿈·스크롤 가능한 UI 확인.
- 일시정지 중 웹페이지를 스크롤해도 모니터 프레임이 유지되는지 파일 해시로 확인.
- 미지정 휴대폰 방향에서 일시정지 안내, 원래 방향으로 복귀 시 출력 재개, 미지정 방향을 재방문하면 다시 지정 안내가 나오는지 확인.
- 가상 모니터 연결 해제 시 공유 종료와 연결 해제 안내 확인.

문서의 v2 실행 이미지는 실제 APK에서 얻었습니다. 테스트용 가상 모니터·로컬 웹페이지 도구는 배포 APK나 소스에 포함하지 않습니다.

**실물 Galaxy S20·USB-C 케이블·제우스랩 모니터의 이번 버전 호환성과 지속 성능·발열은 이 환경에서 검증하지 못했습니다.** 이전 회전 기능의 S20 동작 확인과 이번 2.0 테스트는 구분합니다. Android 14 이상, 폴더블, 모든 One UI 버전의 실기기 검증을 완료했다고 주장하지 않습니다.

## 데이터와 권한

앱은 INTERNET 권한을 선언하지 않습니다. 화면은 외부 출력에만 사용하고 파일로 저장하지 않습니다. 이름을 붙인 영역·배치·방향·화질 설정은 기기 내부 SharedPreferences에 저장합니다.

사용자가 승인한 화면 공유, foreground service, 알림 표시, 다른 앱 위에 표시 권한을 사용합니다. 보호된 콘텐츠는 Android의 공유 제한을 따릅니다. 소리 출력 경로를 변경하거나 별도로 녹음하지 않습니다.

## 플랫폼 참고 문서

- [Android Media projection](https://developer.android.com/media/grow/media-projection): 공유 승인, 세션당 VirtualDisplay 한 번 생성, 화면 크기 변경 시 Surface와 VirtualDisplay 조절.
- [Android 12 터치 통과 제한](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events): 터치 통과 오버레이의 허용 불투명도와 보안 제약.
