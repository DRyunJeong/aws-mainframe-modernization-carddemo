# FTPJCL — FTP 파일 전송 JCL

- **유형**: JCL
- **한 줄 요약**: 메인프레임 데이터셋을 원격 FTP 서버로 전송(PUT)하거나 수신(GET)하기 위한 단일 스텝 FTP 유틸리티 잡.

---

## 기능 설명

`FTPJCLS` 잡은 IBM z/OS에 기본 탑재된 FTP 유틸리티 프로그램(`PGM=FTP`)을 사용하여 메인프레임 데이터셋을 외부 FTP 서버와 교환한다.

현재 소스 기준으로 `SYSIN DD *`에 인라인으로 제공되는 FTP 제어 명령은 다음 동작을 수행한다.

1. IP 주소 `172.31.21.124`의 FTP 서버에 접속한다.
2. 사용자 ID `carddemousr`, 비밀번호 `ftpdemo1`로 인증한다.
3. 전송 모드를 `ASCII`로 설정한다(EBCDIC → ASCII 자동 변환).
4. 원격 서버의 현재 디렉터리(`pwd`)와 파일 목록(`dir`)을 조회한다.
5. 원격 디렉터리를 `/ftpfolder`로 이동한다.
6. 메인프레임 데이터셋 `AWS.M2.CARDEMO.FTP.TEST`를 원격 서버의 `welcome.txt`로 **송신(PUT)** 한다.
7. FTP 세션을 종료한다.

**수신(GET) 전환 방법**: 주석(line 27–28)에 명시된 대로 `PUT` 명령을 `GET welcome.txt 'AWS.M2.CARDEMO.FTP.TEST'`로 교체하면 방향이 역전된다.

> **Java 관점 요약**: 이 잡은 Java의 `org.apache.commons.net.ftp.FTPClient`(Apache Commons Net) 또는 JSch SFTP를 사용하는 단순 파일 업로드/다운로드 유틸리티에 해당한다. 전체 잡이 단 하나의 메서드 호출(`ftpClient.storeFile(...)` 또는 `ftpClient.retrieveFile(...)`)로 치환될 수 있다.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| `STEP1` | `EXEC PGM=FTP,REGION=2048K` | z/OS 내장 FTP 클라이언트를 실행한다. `SYSIN DD *`에 인라인으로 제공된 FTP 제어 스크립트(접속·인증·모드 설정·디렉터리 이동·PUT·QUIT)를 순차 실행한다. `REGION=2048K`로 2 MB 가상 메모리를 할당한다. |

> **참고**: 주석 처리된 `PARM='10.81.148.4 (EXIT TIMEOUT 20'` (line 31)는 `FTP` 프로그램에 대한 대안 파라미터 예시다. `EXIT`는 FTP 명령 오류 시 잡을 비정상 종료시키는 옵션이며, `TIMEOUT 20`은 서버 응답 대기 시간(초)이다. 현재는 비활성 상태이므로 접속 대상 IP는 `SYSIN`의 첫 번째 줄(`172.31.21.124`)로만 지정된다.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 외부 PROC이나 INCLUDE를 참조하지 않는 자급자족형 JCL이다.

- **호출 프로그램 (EXEC PGM)**:
  - `FTP` — z/OS에 기본 탑재된 TCP/IP FTP 클라이언트 유틸리티. 별도 설치 불필요. `SYSIN`의 내용을 FTP 프로토콜 명령으로 해석하여 순차 실행한다.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDEMO.FTP.TEST` (메인프레임 측 송신 소스 데이터셋, QSAM 또는 VSAM 순차 파일로 추정) — PUT 명령의 로컬(메인프레임) 파일명으로 지정됨. (추측: 데이터셋 형식 및 레코드 형식은 소스에 명시되지 않아 런타임 카탈로그 정보에 의존)
  - 원격 서버 파일: `/ftpfolder/welcome.txt` — FTP 서버(`172.31.21.124`)의 수신 경로.
  - `SYSPRINT` DD — FTP 유틸리티가 출력하는 로그(세션 메시지, 응답 코드). JCL에 명시적 DD문이 없으므로 z/OS FTP 유틸리티 기본값(SYSOUT)으로 할당된다. (추측)

- **선행/후행 잡**:
  - **선행 잡**: 명시적 선행 조건 없음. 단, `AWS.M2.CARDEMO.FTP.TEST` 데이터셋이 사전에 존재하고 카탈로그에 등록되어 있어야 한다. CardDemo 배치 사이클 관점에서는 CBEXPORT 또는 데이터셋 생성 잡 이후에 실행하는 것이 논리적이다. (추측)
  - **후행 잡**: 명시적 후행 조건 없음. 수신(GET) 방향으로 전환 시 후속 데이터 로드 잡(예: CBIMPORT 계열)이 이어질 수 있다. (추측)

---

## Java/현대화 노트

### 1. EBCDIC ↔ ASCII 변환 (`ASCII` 제어 명령)

FTP `ASCII` 모드(line 36)는 z/OS FTP 클라이언트가 EBCDIC 인코딩으로 저장된 메인프레임 데이터를 전송 시 자동으로 ASCII로 변환함을 의미한다. Java 현대화 시 반드시 인코딩을 명시해야 한다.

```java
// 메인프레임 파일을 읽을 때: EBCDIC(IBM037) → UTF-8 변환
try (InputStream raw = new FileInputStream("AWS_M2_CARDEMO_FTP_TEST");
     InputStreamReader reader = new InputStreamReader(raw, Charset.forName("IBM037"))) {
    // ...
}
```

바이너리 데이터(COMP-3 packed decimal 등)가 포함된 파일은 `ASCII` 모드 대신 `BINARY` 모드를 사용해야 한다. 이 잡의 대상 파일이 순수 텍스트인지 이진 데이터를 포함하는지 확인 필요.

### 2. 인라인 평문 자격증명 (보안 위험)

`SYSIN DD *`에 사용자 ID `carddemousr`와 비밀번호 `ftpdemo1`이 평문으로 하드코딩되어 있다(line 34–35). 메인프레임에서는 JCL이 보통 보호된 PDS 멤버에 저장되어 접근이 제한되지만, Java 현대화 시에는 반드시 비밀을 외부화해야 한다.

```java
// 권장: AWS Secrets Manager 또는 환경 변수에서 자격증명 로드
String password = System.getenv("FTP_PASSWORD");
// 또는
SecretsManagerClient client = SecretsManagerClient.create();
String password = client.getSecretValue(r -> r.secretId("carddemo/ftp")).secretString();
```

### 3. FTP → SFTP/S3 전환 권고

평문 FTP 프로토콜은 보안상 권장되지 않는다. AWS 환경으로 이전 시 다음 중 하나를 선택한다:

| 메인프레임 방식 | Java/AWS 현대화 대안 |
|---------------|---------------------|
| `PGM=FTP` + PUT | `FTPClient.storeFile()` (Apache Commons Net) |
| FTP → 원격 서버 | AWS Transfer Family (SFTP endpoint) |
| PUT to `/ftpfolder` | S3 `PutObjectRequest` (`software.amazon.awssdk:s3`) |

```java
// S3 업로드 예시 (가장 권장되는 AWS 현대화 경로)
S3Client s3 = S3Client.builder().region(Region.US_EAST_1).build();
s3.putObject(
    PutObjectRequest.builder()
        .bucket("carddemo-ftp-bucket")
        .key("ftpfolder/welcome.txt")
        .build(),
    Paths.get("/local/AWS_M2_CARDEMO_FTP_TEST")
);
```

### 4. `REGION=2048K` — 메모리 의미

JCL의 `REGION=2048K`는 이 잡 스텝에 2 MB 가상 주소 공간을 할당한다. FTP 클라이언트에는 충분한 크기이며 Java에서는 JVM 힙 설정(`-Xmx`)으로 대응되지만, 단순 파일 전송은 스트리밍 처리로 힙 크기에 무관하게 처리 가능하다.

### 5. `TIMEOUT` 파라미터 (비활성 상태)

주석 처리된 `PARM='10.81.148.4 (EXIT TIMEOUT 20'`의 `TIMEOUT 20`은 20초 접속 타임아웃을 의미한다. Java 현대화 시 명시적으로 설정해야 한다.

```java
FTPClient ftp = new FTPClient();
ftp.setConnectTimeout(20_000);  // 20초 (밀리초 단위)
ftp.setDefaultTimeout(20_000);
```

### 6. 오류 처리 부재

현재 JCL에는 `COND` 파라미터나 `IF/THEN/ELSE` 조건문이 없다. FTP 명령 실패(예: 접속 오류, 인증 실패) 시 잡이 비정상 종료(`ABEND`)되거나 경고 리턴 코드를 남길 수 있으나 별도 처리 로직이 없다. `EXIT` 옵션(주석 처리됨)을 활성화하면 첫 FTP 오류에서 즉시 중단하는 동작을 얻을 수 있다. Java에서는 try-catch와 재시도 로직(예: Spring Retry)으로 구현한다.
