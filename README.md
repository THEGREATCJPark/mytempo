# mytempo

무료 · 광고 없음 · 오프라인 Android 메트로놈

## 바로 설치

**[Android APK 다운로드](downloads/mytempo-1.2.apk?raw=true)** · Android 8.0 이상

파일을 내려받아 열고 기기의 설치 안내를 따르세요. 앱 내부 표시 이름은 Pulse Metronome입니다. 이전 Pulse 1.2 설치와 같은 서명을 사용합니다.

## 기능

- 10–400 BPM, 탭 템포, 30종 박자, 박별 강세·무음
- BPM 버튼: 짧게 누르면 1씩, 길게 누르면 5씩 변경
- 백그라운드 재생과 알림에서 정지
- 숫자로 보는 재즈 스케일 23종 및 추천 학습 순서
- 코드별 Available Tension 참고표
- 자체 합성 사운드 12종, 음량, 테마, 화면 유지

광고·계정·분석 SDK가 없으며 인터넷 권한을 요청하지 않습니다. 텐션과 학습 순서는 연습을 위한 참고 기준입니다. 코드 기능·멜로디·보이싱에 따라 적용이 달라집니다.

## 빌드와 검증

JDK 17+, Android SDK platform 35 / build-tools 35.0.0, Python 3, zip이 필요합니다.

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
bash build.sh
bash tests/run-audio-tests.sh
```

서명 키는 공개 저장소에 포함하지 않습니다. 로컬 빌드 시 개인 서명 키를 생성합니다. 배포 APK와 다른 키로 빌드하면 기존 배포 앱 위에 업데이트 설치할 수 없습니다.

버전 1.2는 빌드·서명 및 오디오 엔진 자동 검증을 통과했습니다. 실제 휴대폰의 음향 지연과 블루투스 페달 호환은 별도 확인이 필요합니다.
