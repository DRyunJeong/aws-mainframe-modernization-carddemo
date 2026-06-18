# TXT2PDF1 — 텍스트 파일을 PDF로 변환

- **유형**: JCL
- **한 줄 요약**: CardDemo 명세서 텍스트 파일(`STATEMNT.PS`)을 PDF로 변환하는 단일-스텝 유틸리티 잡으로, REXX/TSO 환경에서 `TXT2PDF` REXX 프로그램을 실행한다.

---

## 기능 설명

이 잡은 CBSTM03A/CBSTM03B 배치 잡이 생성한 명세서 텍스트 파일(`AWS.M2.CARDDEMO.STATEMNT.PS`)을 PDF 포맷(`AWS.M2.CARDDEMO.STATEMNT.PS.PDF`)으로 변환하는 후처리 단계다.

변환 방식은 메인프레임 고유의 TSO 배치 실행기(IKJEFT1B)를 통해 REXX Exec인 `TXT2PDF`를 호출하는 구조다. IKJEFT1B는 TSO 명령·REXX Exec을 배치 환경에서 실행할 수 있게 해주는 IBM 제공 프로그램으로, Java로 비유하면 `ProcessBuilder`로 외부 스크립트를 실행하는 것과 유사하다.

`SYSTSIN` DD에 인라인으로 제공된 REXX 호출 명령은 다음과 같다 (라인 38–39):

```
%TXT2PDF BROWSE Y IN DD:INDD +
OUT 'AWS.M2.CARDDEMO.STATEMNT.PS.PDF'
```

- `%TXT2PDF` — `SYSEXEC` 라이브러리에서 찾는 REXX Exec 이름 (앞의 `%`는 REXX Exec를 직접 호출하는 TSO 관례)
- `BROWSE Y` — 변환 후 PDF 미리보기 여부 (배치 환경에서는 실질적으로 무시되거나 로그 출력용)
- `IN DD:INDD` — 입력을 `INDD` DD명이 가리키는 데이터셋에서 읽음 (DD 간접 참조)
- `OUT 'AWS.M2.CARDDEMO.STATEMNT.PS.PDF'` — 출력 PDF 데이터셋 이름을 직접 지정 (출력 DD 없이 TSO 할당으로 처리됨, 추측)

`COND=(0,NE)` 조건은 이전 스텝이 RC=0일 때만 이 스텝을 실행하라는 의미이나, 이 잡에는 스텝이 하나뿐이므로 실질적 효과는 없다(단독 실행 시 항상 수행됨).

---

## 스텝 구성

| 스텝명   | EXEC PGM/PROC   | 역할                                                                                     |
|----------|-----------------|------------------------------------------------------------------------------------------|
| TXT2PDF  | PGM=IKJEFT1B    | IBM TSO 배치 실행기를 기동하여 `SYSEXEC` 라이브러리의 REXX Exec `TXT2PDF`를 수행, 텍스트 명세서를 PDF로 변환 |

**주요 DD 설명**

| DD명     | 내용                                             |
|----------|--------------------------------------------------|
| STEPLIB  | TXT2PDF 전용 Load Library (`AWS.M2.LBD.TXT2PDF.LOAD`) — IKJEFT1B가 여기서 관련 모듈을 로드 |
| SYSEXEC  | TXT2PDF REXX Exec가 저장된 라이브러리 (`AWS.M2.LBD.TXT2PDF.EXEC`) |
| INDD     | 입력 명세서 텍스트 파일 (`AWS.M2.CARDDEMO.STATEMNT.PS`) |
| SYSPRINT | TSO/REXX 시스템 메시지 출력 (SYSOUT=*) |
| SYSTSPRT | TSO 출력 스풀 (SYSOUT=*) |
| SYSTSIN  | REXX 호출 명령 인라인 스트림 |

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — PROC 호출 없이 단순 `EXEC PGM=IKJEFT1B` 직접 실행

- **호출 프로그램 (EXEC PGM)**:
  - `IKJEFT1B` — IBM 제공 TSO 배치 터미널 모니터 프로그램(TMP). 메인프레임 표준 유틸리티이며 별도 설치 불필요
  - `TXT2PDF` (REXX Exec) — `AWS.M2.LBD.TXT2PDF.EXEC` 라이브러리에 저장된 사제(vendor 또는 오픈소스) REXX 스크립트. CardDemo 배포본의 일부이나 이 JCL 파일 내에 소스가 포함되어 있지 않으므로 별도 확인 필요

- **데이터셋/파일/DB 테이블**:
  - 입력: `AWS.M2.CARDDEMO.STATEMNT.PS` — CBSTM03A/B가 생성한 명세서 순차 파일(Physical Sequential, RECFM=FB 추측)
  - 출력: `AWS.M2.CARDDEMO.STATEMNT.PS.PDF` — TXT2PDF REXX가 TSO ALLOCATE 또는 라이브러리 내부 방식으로 생성하는 PDF 데이터셋 (JCL 내 출력 DD 미정의이므로 REXX 내부에서 동적 할당하는 것으로 추측)
  - 실행 라이브러리: `AWS.M2.LBD.TXT2PDF.LOAD`, `AWS.M2.LBD.TXT2PDF.EXEC`

- **선행/후행 잡**:
  - 선행: `CREASTMT` (CBSTM03A/B — 명세서 텍스트 파일 생성 잡). 해당 잡이 `STATEMNT.PS`를 생성해야 이 잡이 의미 있음
  - 후행: 없음 — PDF 생성이 최종 단계. 생성된 PDF는 배포(FTP 전송, 아카이브 등) 프로세스로 연결될 수 있으나 이 JCL 범위 밖임

---

## Java/현대화 노트

### IKJEFT1B + REXX → 외부 프로세스 실행

메인프레임에서 IKJEFT1B는 TSO 환경을 통해 스크립트(REXX)를 실행하는 런타임 호스트다. Java 현대화 관점에서는 다음과 같이 대응된다:

```java
// IKJEFT1B + REXX TXT2PDF 호출의 Java 등가 구조
ProcessBuilder pb = new ProcessBuilder(
    "txt2pdf",          // TXT2PDF REXX → 외부 PDF 변환 도구 (예: iText, Apache PDFBox CLI)
    "--input",  inputTextPath,
    "--output", outputPdfPath
);
pb.redirectErrorStream(true);
Process process = pb.start();
int rc = process.waitFor();
if (rc != 0) throw new RuntimeException("PDF 변환 실패: rc=" + rc);
```

### 텍스트→PDF 변환의 현대적 대안

| 메인프레임 구성요소 | Java/현대 대안 |
|---------------------|----------------|
| REXX TXT2PDF Exec   | Apache PDFBox (`PDDocument`), iText 7, OpenPDF |
| SYSEXEC 라이브러리  | Maven/Gradle 의존성 |
| SYSTSIN 인라인 명령 | 메서드 파라미터 |
| DD:INDD 간접 참조   | `InputStream` / `Path` 인자 |
| TSO 동적 할당(추측) | `Files.createTempFile()` 또는 출력 경로 직접 지정 |

### DD 간접 참조 (`IN DD:INDD`) 주의사항

`IN DD:INDD`는 TSO/REXX 환경에서 DD명을 직접 파일 경로 대신 쓸 수 있게 해주는 메인프레임 고유 문법이다. Java에는 직접 대응 개념이 없으며, 마이그레이션 시 DD명 기반 참조를 실제 파일 경로(또는 `InputStream`)로 명시적으로 교체해야 한다.

### 출력 데이터셋 동적 할당 (추측)

출력 PDF(`AWS.M2.CARDDEMO.STATEMNT.PS.PDF`)에 대한 JCL DD 정의가 없다 (라인 38–39). 이는 TXT2PDF REXX Exec 내부에서 `ALLOCATE FILE(OUTDD) DA(...)` 등으로 동적 할당하거나, 사전에 카탈로그된 데이터셋에 덮어쓰는 방식일 가능성이 높다. Java 이식 시 이 부분을 REXX 소스에서 직접 확인해야 한다.

### COND=(0,NE) 단독 스텝에서의 의미

라인 25의 `COND=(0,NE)`는 "이전 스텝 RC가 0이 아니면 이 스텝을 건너뛰라"는 의미다. 스텝이 하나뿐인 이 잡에서는 실질적으로 무의미하나, 향후 이 잡에 선행 스텝이 추가될 경우를 대비한 방어 코딩으로 볼 수 있다. Java CI/CD 파이프라인에서는 `if: steps.previous.outcome == 'success'` 조건에 해당한다.
