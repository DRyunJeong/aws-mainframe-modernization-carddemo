# READCARD — 카드 마스터 파일 순차 읽기/덤프 잡

- **유형**: JCL (단일 스텝 배치 잡)
- **한 줄 요약**: VSAM KSDS 카드 마스터 파일(`AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS`)을 처음부터 끝까지 순차적으로 읽어 모든 레코드를 SYSOUT으로 출력하는 진단·덤프용 배치 잡.

---

## 기능 설명

READCARD 잡은 CardDemo의 카드 마스터 VSAM KSDS 파일 전체를 순차 스캔하여 각 카드 레코드(150바이트)를 콘솔/SYSOUT에 그대로 출력한다. 이 잡의 목적은 신규 환경 구축 후 데이터 검증, 파일 내용 확인, 또는 문제 발생 시 빠른 덤프를 얻기 위한 운영 진단 도구이다.

실제 처리 로직은 모두 COBOL 프로그램 `CBACT02C`에 있으며, JCL은 실행 환경(로드 라이브러리 위치, 파일 연결, 출력 대상)만 지정한다.

### 프로그램 CBACT02C 내부 흐름 (`app/cbl/CBACT02C.cbl`)

| 순서 | 단락 | 동작 |
|------|------|------|
| 1 | `0000-CARDFILE-OPEN` | `OPEN INPUT CARDFILE-FILE` — VSAM KSDS를 읽기 전용으로 오픈. `CARDFILE-STATUS ≠ '00'`이면 `9999-ABEND-PROGRAM` 호출 |
| 2 | `PERFORM UNTIL END-OF-FILE = 'Y'` | 메인 READ 루프. EOF 플래그가 'Y'가 될 때까지 반복 |
| 3 | `1000-CARDFILE-GET-NEXT` | `READ CARDFILE-FILE INTO CARD-RECORD` — 다음 레코드를 `CARD-RECORD`(CVACT02Y copybook)로 읽기. `status='00'` → 정상, `'10'` → EOF(END-OF-FILE='Y' 설정), 그 외 → `9999-ABEND-PROGRAM` |
| 4 | `DISPLAY CARD-RECORD` | EOF가 아닌 경우 150바이트 카드 레코드를 `SYSOUT`에 출력 |
| 5 | `9000-CARDFILE-CLOSE` | `CLOSE CARDFILE-FILE`. 오류 시 ABEND |
| 6 | `GOBACK` | 프로그램 종료 (반환코드 0) |

오류 처리: I/O 오류가 발생하면 `9910-DISPLAY-IO-STATUS`로 상태 코드를 4자리로 변환해 표시한 뒤, `CALL 'CEE3ABD' USING ABCODE(999), TIMING(0)`으로 강제 abend(U0999)한다.

> **참고**: `1000-CARDFILE-GET-NEXT` 안에 `* DISPLAY CARD-RECORD` 주석 처리된 라인(`CBACT02C.cbl` 96번 줄)이 있다. 실제 DISPLAY는 READ 루프 바깥(`CBACT02C.cbl` 78번 줄)에서 수행되므로 동작상 차이는 없으나, 개발 중간에 위치를 옮긴 흔적이다.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| `STEP05` | `PGM=CBACT02C` | 카드 마스터 KSDS를 SEQUENTIAL ACCESS로 순차 읽어 모든 레코드를 SYSOUT에 DISPLAY. 로드 모듈은 `AWS.M2.CARDDEMO.LOADLIB`에서 로드 |

(전체 1개 스텝만 존재)

---

## 의존성

### COPY (PROC/INCLUDE)
- 없음. 이 JCL은 PROC나 INCLUDE를 사용하지 않는다. 컴파일 시 `CBACT02C.cbl` 내부에서 `COPY CVACT02Y`(카드 레코드 구조)를 사용한다.

### 호출 프로그램 (EXEC PGM)
- **`CBACT02C`** (`app/cbl/CBACT02C.cbl`) — 카드 마스터 VSAM KSDS 순차 읽기/덤프 배치 프로그램.
  - `COPY CVACT02Y` (`app/cpy/CVACT02Y.cpy`): 150바이트 카드 레코드 구조 (`CARD-NUM` X(16) · `CARD-ACCT-ID` 9(11) · `CARD-CVV-CD` 9(3) · `CARD-EMBOSSED-NAME` X(50) · `CARD-EXPIRAION-DATE` X(10) · `CARD-ACTIVE-STATUS` X(1) · FILLER X(59))

### 데이터셋/파일/DB 테이블

| DD명 | 데이터셋 | 유형 | 용도 |
|------|---------|------|------|
| `STEPLIB` | `AWS.M2.CARDDEMO.LOADLIB` | PDS (로드 라이브러리) | CBACT02C 로드 모듈 위치 |
| `CARDFILE` | `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS` | VSAM KSDS (기본키=카드번호 16바이트, 레코드 150바이트) | 카드 마스터 파일 (읽기 전용) |
| `SYSOUT` | `SYSOUT=*` | SYSOUT 클래스 | DISPLAY된 카드 레코드 및 상태 메시지 출력 |
| `SYSPRINT` | `SYSOUT=*` | SYSOUT 클래스 | 시스템 메시지 (주로 런타임 진단) |

- CARDFILE의 키 구조: 기본 키 오프셋 0, 길이 16(카드번호). 대체 인덱스(AIX) `AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX`는 오프셋 16, 길이 11(계정ID — `CARDFILE.jcl` 참조)이나 이 잡에서는 AIX를 사용하지 않는다.

### 선행/후행 잡

| 구분 | 잡/잡스텝 | 설명 |
|------|---------|------|
| 선행 (필수) | `CARDFILE.jcl` (`STEP15`) | VSAM KSDS에 카드 데이터를 REPRO로 로드하는 초기화 잡. 이 잡이 성공한 후에야 READCARD를 실행해야 한다 |
| 선행 (운영 시) | `CLOSEFIL.jcl` 또는 `CARDFILE.jcl` `CLCIFIL` 스텝 | CICS가 CARDFILE을 열어두고 있으면 충돌 가능. CICS `CEMT SET FIL(CARDDAT) CLO`로 파일을 닫은 후 실행 권장 |
| 후행 | 없음 (독립 진단 잡) | 야간 배치 시퀀스(POSTTRAN→INTCALC→…)와 무관. 단독으로 언제든 실행 가능 |

---

## Java/현대화 노트

### 1. JCL → Java 구조 대응

```
// JCL STEP05: PGM=CBACT02C, CARDFILE DD DSN=..., SYSOUT=*
```

Java/Spring Batch로 치환하면 다음과 같다:

```java
// Spring Batch Job 정의
@Bean
public Job readCardJob(Step readCardStep) {
    return jobBuilderFactory.get("readCardJob")
            .start(readCardStep)
            .build();
}

@Bean
public Step readCardStep(FlatFileItemReader<CardRecord> reader,
                         ItemWriter<CardRecord> sysoutWriter) {
    return stepBuilderFactory.get("readCardStep")
            .<CardRecord, CardRecord>chunk(100)
            .reader(reader)       // CARDFILE DD → ItemReader
            .writer(sysoutWriter) // DISPLAY → ItemWriter(stdout/로그)
            .build();
}
```

### 2. VSAM KSDS → Java 파일 접근

COBOL `SELECT CARDFILE-FILE ASSIGN TO CARDFILE / ORGANIZATION IS INDEXED / ACCESS MODE IS SEQUENTIAL`는 VSAM KSDS를 키 순서(카드번호 오름차순)로 순차 접근한다는 의미이다.

- Java 현대화 환경에서 VSAM을 직접 접근할 수 없으므로, AWS Mainframe Modernization(M2) 또는 Micro Focus 등 VSAM 에뮬레이션 레이어를 통하거나, 마이그레이션 시 관계형 DB 테이블(`CARD` 테이블)로 전환 후 JPA/JDBC로 접근한다.
- 단순 덤프 용도라면 `SELECT * FROM CARD ORDER BY CARD_NUM`으로 완전히 대체 가능하다.

### 3. CVACT02Y copybook → Java DTO

```cobol
01  CARD-RECORD.
    05  CARD-NUM                PIC X(16).     -- 카드번호 (고정 16자)
    05  CARD-ACCT-ID            PIC 9(11).     -- 계정ID (숫자 11자리)
    05  CARD-CVV-CD             PIC 9(03).     -- CVV (숫자 3자리)
    05  CARD-EMBOSSED-NAME      PIC X(50).     -- 카드 소유자명 (고정 50자, 공백 패딩)
    05  CARD-EXPIRAION-DATE     PIC X(10).     -- 만료일 (YYYY-MM-DD 추정, 필드명 오타)
    05  CARD-ACTIVE-STATUS      PIC X(01).     -- 활성 여부 ('Y'/'N' 추측)
    05  FILLER                  PIC X(59).     -- 패딩 (미사용)
```

```java
public class CardRecord {
    private String  cardNum;          // PIC X(16) — String, trim() 필요
    private long    cardAcctId;       // PIC 9(11) — long (최대 99,999,999,999)
    private int     cardCvvCd;        // PIC 9(03) — int
    private String  cardEmbossedName; // PIC X(50) — String, trim() 필요
    private String  cardExpirationDate; // PIC X(10) — LocalDate로 파싱 권장
    private char    cardActiveStatus; // PIC X(01) — char 또는 boolean
    // FILLER 59바이트 → Java에서는 무시
}
```

> 주의: `CARD-EXPIRAION-DATE` 필드명에 오타(Expiration → Expiraion)가 있다(`CVACT02Y.cpy` 9번 줄). 마이그레이션 시 컬럼명 정규화 필요.

### 4. FILE STATUS 패턴 → 예외 처리

| COBOL FILE STATUS | 의미 | Java 대응 |
|------------------|------|----------|
| `'00'` | 정상 | — |
| `'10'` | EOF | `StopIteration` / ItemReader가 `null` 반환 |
| 그 외 | I/O 오류 | `IOException` throw → Spring Batch skip/retry 정책 |

### 5. CEE3ABD → 예외 전파

`CALL 'CEE3ABD' USING ABCODE(999), TIMING(0)` (`CBACT02C.cbl` 158번 줄)은 LE(Language Environment) 서비스 루틴을 호출해 U0999 abend를 발생시킨다. Java에서는 `throw new RuntimeException("ABEND U0999: ...")` 또는 Spring Batch의 `JobExecutionException`으로 대응한다. Abend 코드는 JCL의 COND 파라미터나 후속 잡의 선행조건 체크에 사용되므로, Java 마이그레이션 시 반환코드(exit code) 설계가 필요하다.

### 6. 이 잡의 야간 배치 내 위치

READCARD는 야간 배치 시퀀스(POSTTRAN→INTCALC→TRANBKP→COMBTRAN→TRANIDX→OPENFIL)와 독립된 단독 진단 잡이다. 운영 중 CARDFILE을 CICS가 열어두고 있을 경우 `DISP=SHR`로도 충돌이 발생할 수 있으므로(VSAM SHAREOPTIONS(2,3)이나 CICS ENQUEUE 정책에 따라 다름), 실행 전 CICS에서 파일을 닫거나 배치 윈도우(야간 CICS 다운 타임) 중에 실행하는 것이 안전하다.

---

*소스 버전: `CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19` (READCARD.jcl 30번 줄) / `CardDemo_v2.0-25-gdb72e6b-235 Date: 2025-04-29` (CBACT02C.cbl 177번 줄)*
