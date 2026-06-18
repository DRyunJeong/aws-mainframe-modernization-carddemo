# READACCT — 계정 마스터 읽기 및 다중 포맷 덤프 잡

- **유형**: JCL (배치 유틸리티 잡)
- **한 줄 요약**: VSAM KSDS 계정 마스터 파일(`ACCTFILE`)을 순차 스캔하여 세 가지 서로 다른 레코드 레이아웃(고정 길이 DISPLAY, ARRAY, 가변 길이 VB)으로 각각 출력 파일에 덤프하는 개발/검증용 유틸리티 잡.

---

## 기능 설명

READACCT 잡은 두 개의 스텝으로 구성된다.

첫 번째 스텝(`PREDEL`)은 이전 실행에서 남은 출력 파일 세 개를 삭제하여 실행 환경을 초기화한다. `IEFBR14`는 실제 처리를 수행하지 않는 null 프로그램이며, DD 카드의 `DISP=(MOD,DELETE,DELETE)` 지시어만으로 삭제 효과를 낸다.

두 번째 스텝(`STEP05`)은 실제 작업을 수행하는 `CBACT01C` 프로그램을 실행한다. 이 프로그램은 계정 VSAM KSDS를 순차(SEQUENTIAL) 모드로 읽어 레코드마다 다음 세 가지 방식으로 출력한다.

1. **OUTFILE(PSCOMP)**: 계정 필드를 DISPLAY/COMP-3 혼합 고정 레이아웃(LRECL=107, FB)으로 플랫 파일에 기록. `ACCT-CURR-CYC-DEBIT`이 0이면 하드코딩 값 2525.00을 대신 기록하고, 재발행일(`ACCT-REISSUE-DATE`)은 Assembler 서브루틴 `COBDATFT`를 호출하여 날짜 포맷을 변환한 값으로 기록한다.

2. **ARRYFILE(ARRYPS)**: `OCCURS 5 TIMES` 배열 구조(LRECL=110, FB)로 기록. 각 계정당 슬롯 1~2에는 실제 잔액과 하드코딩 차변(1005.00, 1525.00)을, 슬롯 3에는 음수 고정값(-1025.00 / -2500.00)을 기록한다. 이는 배열 직렬화 패턴을 테스트하기 위한 의도적 데이터이다.

3. **VBRCFILE(VBPS)**: 가변 길이(VB, LRECL=84) 파일에 레코드당 두 개의 VB 레코드를 기록한다. `VBRC-REC1`(12바이트: 계정ID+활성상태)과 `VBRC-REC2`(39바이트: 계정ID+잔액+신용한도+재발행년도)가 계정 하나당 쌍으로 기록된다. `WS-RECD-LEN`에 길이를 직접 설정하여 WRITE 전에 VB 레코드 크기를 제어한다.

각 레코드는 SYSOUT에 `DISPLAY`로도 출력되어 사람이 읽을 수 있는 덤프 로그를 남긴다. 이 잡은 운영 배치 시퀀스(야간 배치)에 포함되지 않으며, 파일 레이아웃 검증이나 마이그레이션 분석 목적의 독립 실행형 유틸리티 잡이다.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| `PREDEL` | `EXEC PGM=IEFBR14` | 이전 실행 출력 파일 3개를 `DISP=(MOD,DELETE,DELETE)`로 사전 삭제. 실제 코드 없음, DD 처리만으로 삭제 수행 |
| `STEP05` | `EXEC PGM=CBACT01C` | VSAM KSDS 계정 파일 순차 읽기 → 3가지 포맷(FB-DISPLAY, FB-ARRAY, VB)으로 각 출력 파일에 기록 + SYSOUT 덤프 |

---

## 의존성

### COPY (PROC/INCLUDE)
- 해당 없음. 이 JCL은 카탈로그 프로시저나 JCLLIB INCLUDE를 사용하지 않는다.

### 호출 프로그램 (EXEC PGM)
- `IEFBR14` — IBM 제공 null 프로그램. 아무 처리도 하지 않으며 JCL의 파일 할당/해제 메커니즘을 통해 파일 삭제에만 활용된다.
- `CBACT01C` — CardDemo 배치 COBOL 프로그램(`app/cbl/CBACT01C.cbl`). 계정 파일을 읽어 세 종류 출력 파일에 기록. 내부에서 Assembler 서브루틴 `COBDATFT`를 `CALL`로 호출하여 날짜 포맷(`YYYY-MM-DD` → `YYYYMMDD` 또는 역방향)을 변환한다(`app/asm/` 소재). 오류 시 `CALL 'CEE3ABD'`로 abend(코드 999)를 발생시킨다.

### 데이터셋/파일/DB 테이블

| DD명 | 데이터셋명 | 방향 | 설명 |
|------|-----------|------|------|
| `DD01` (PREDEL) | `AWS.M2.CARDDEMO.ACCTDATA.PSCOMP` | DELETE | 이전 실행 FB-DISPLAY 출력 삭제 |
| `DD02` (PREDEL) | `AWS.M2.CARDDEMO.ACCTDATA.ARRYPS` | DELETE | 이전 실행 FB-ARRAY 출력 삭제 |
| `DD03` (PREDEL) | `AWS.M2.CARDDEMO.ACCTDATA.VBPS` | DELETE | 이전 실행 VB 출력 삭제 |
| `ACCTFILE` | `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS` | INPUT (SHR) | 계정 마스터 VSAM KSDS. CBACT01C의 SELECT...ASSIGN TO ACCTFILE과 연결됨. 레코드 길이 300바이트(CVACT01Y 기준: 11+1+12+12+12+10+10+10+12+12+10+10+178) |
| `OUTFILE` | `AWS.M2.CARDDEMO.ACCTDATA.PSCOMP` | OUTPUT | 고정 길이(LRECL=107, RECFM=FB) DISPLAY/COMP-3 혼합 출력. SPACE=(CYL,(1,2),RLSE) |
| `ARRYFILE` | `AWS.M2.CARDDEMO.ACCTDATA.ARRYPS` | OUTPUT | 배열 구조 고정 길이(LRECL=110, RECFM=FB) 출력. SPACE=(CYL,(1,2),RLSE) |
| `VBRCFILE` | `AWS.M2.CARDDEMO.ACCTDATA.VBPS` | OUTPUT | 가변 길이(LRECL=84, RECFM=VB) 출력. 계정당 VB1(12바이트)+VB2(39바이트) 두 레코드 기록 |
| `STEPLIB` | `AWS.M2.CARDDEMO.LOADLIB` | SHR | CBACT01C 및 COBDATFT 로드 모듈 라이브러리 |
| `SYSOUT`, `SYSPRINT` | `SYSOUT=*` | OUTPUT | 콘솔/DISPLAY 출력 및 시스템 메시지 |

### 선행/후행 잡
- **선행 잡**: ACCTFILE(`AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS`)이 사전에 로드되어 있어야 한다. 야간 배치 시퀀스에서는 마스터 파일 갱신 잡(ACCTFILE.jcl 등 초기 로드 또는 POSTTRAN/INTCALC 이후)이 선행된다.
- **후행 잡**: READACCT는 독립 유틸리티 잡이므로 공식 야간 배치 시퀀스(POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL)에 포함되지 않는다. 생성된 세 출력 파일은 후속 분석이나 마이그레이션 검증에 사용된다.

---

## Java/현대화 노트

### 1. IEFBR14 패턴 — "삭제 전용 스텝"
JCL 라인 22~28의 `PREDEL` 스텝은 Java 마이그레이션 시 `Files.deleteIfExists(path)`에 해당한다. 메인프레임에서는 파일을 덮어쓰기 전에 이전 카탈로그 항목을 먼저 삭제해야 하므로 이 패턴이 관용적으로 쓰인다. Spring Batch에서는 `JobExecutionListener.beforeJob()`에서 출력 파일을 삭제하거나 `FileSystemResource`를 `overwrite=true`로 설정하면 된다.

### 2. RECFM=FB vs RECFM=VB — 고정 vs 가변 레코드
이 잡은 의도적으로 두 레코드 포맷을 모두 사용한다.
- `OUTFILE`(LRECL=107, RECFM=FB): 모든 레코드가 정확히 107바이트. Java 대응: `byte[107]` 고정 배열 또는 `ByteBuffer.wrap(byte[107])`. 읽을 때는 정확히 107바이트씩 잘라 파싱한다.
- `ARRYFILE`(LRECL=110, RECFM=FB): OCCURS 5개 슬롯을 직렬화. Java 대응: `List<BalanceEntry>` (크기 5 고정)를 직렬화한 구조.
- `VBRCFILE`(LRECL=84, RECFM=VB): 실제 데이터 길이가 레코드마다 다름(12 또는 39바이트). 메인프레임 VB 포맷은 앞에 4바이트 RDW(Record Descriptor Word)가 붙어 실제 길이를 기록한다. Java로 읽을 때는 먼저 4바이트 RDW를 읽어 레코드 길이를 파악한 후 해당 길이만큼 읽어야 한다.

### 3. OCCURS 5 TIMES — 배열 직렬화 (라인 74~78)
```cobol
05  ARR-ACCT-BAL OCCURS 5  TIMES.
  10  ARR-ACCT-CURR-BAL        PIC S9(10)V99.
  10  ARR-ACCT-CURR-CYC-DEBIT  PIC S9(10)V99 USAGE IS COMP-3.
```
- `ARR-ACCT-CURR-BAL`: `PIC S9(10)V99` DISPLAY 포맷 → 부호 포함 12바이트 EBCDIC 숫자. Java: `BigDecimal`.
- `ARR-ACCT-CURR-CYC-DEBIT`: `PIC S9(10)V99 COMP-3` → packed decimal, 부호 포함 7바이트. Java: 변환 라이브러리 필요(예: `net.sf.cb2java` 또는 수동 nibble 파싱).
- 슬롯 1~2는 실제 잔액값, 슬롯 3은 하드코딩 음수, 슬롯 4~5는 미초기화(INITIALIZE로 0으로 설정됨). 이는 배열 포맷 테스트 목적이지 실 업무 데이터가 아니다(라인 253~261 참조).

### 4. COMP-3(Packed Decimal) 주의사항
```cobol
05  OUT-ACCT-CURR-CYC-DEBIT    PIC S9(10)V99 USAGE IS COMP-3.
```
`PIC S9(10)V99 COMP-3`은 총 12자리+부호를 packed decimal로 저장하므로 `(12+1)/2 = 7`바이트를 차지한다. 소수점은 실제로 기록되지 않고 V 위치만 묵시적으로 처리된다. Java로 이 필드를 읽을 때 `BigDecimal`로 변환하고 `scale=2`를 명시해야 한다. `double`이나 `float`는 정밀도 손실이 발생하므로 절대 사용하지 않는다.

### 5. COBDATFT 날짜 변환 서브루틴 (라인 231)
```cobol
CALL 'COBDATFT' USING CODATECN-REC.
```
`CODATECN-REC`(app/cpy/CODATECN.cpy)는 입력 타입(`1`=YYYYMMDD, `2`=YYYY-MM-DD), 입력 날짜, 출력 타입, 출력 날짜 필드를 하나의 레코드에 담는 파라미터 블록이다. `REDEFINES`로 동일 메모리를 두 가지 레이아웃으로 해석한다. Java 대응:
```java
// CODATECN-REC 전체가 하나의 파라미터 객체 역할
DateConversionResult result = DateConverter.convert(
    DateConversionRequest.of(inputType, inputDate, outputType));
```
`COBDATFT`는 `app/asm/` 소재 Assembler 모듈로, Java 마이그레이션 시 `java.time.format.DateTimeFormatter`로 대체 가능하다.

### 6. TWO-BYTES-BINARY REDEFINES 패턴 (라인 107~110)
```cobol
01  TWO-BYTES-BINARY    PIC 9(4) BINARY.
01  TWO-BYTES-ALPHA     REDEFINES TWO-BYTES-BINARY.
    05  TWO-BYTES-LEFT  PIC X.
    05  TWO-BYTES-RIGHT PIC X.
```
동일 2바이트 메모리를 정수와 두 개의 문자 양쪽으로 해석하는 union 패턴이다. 9910-DISPLAY-IO-STATUS 단락에서 비정상 파일 상태(첫 바이트가 '9'인 경우)를 숫자로 변환할 때 사용한다. Java에는 직접 대응하는 구조가 없으며, `ByteBuffer` 또는 비트 연산으로 구현해야 한다. **REDEFINES는 Java에 직접 번역되지 않으므로**, 용도별로 별도 변수를 선언하고 명시적 변환 메서드를 작성한다.

### 7. CEE3ABD abend 패턴 (라인 406~410)
```cobol
9999-ABEND-PROGRAM.
    MOVE 0   TO TIMING
    MOVE 999 TO ABCODE
    CALL 'CEE3ABD' USING ABCODE, TIMING.
```
`CEE3ABD`는 IBM Language Environment의 강제 abend 루틴이다. abend 코드 999로 잡을 비정상 종료시킨다. Java 대응: `throw new RuntimeException("ABEND 999: ...")` 또는 Spring Batch의 `StepExecution.setTerminateOnly()` + `throw new FatalStepExecutionException(...)`. 메인프레임 JCL에서는 abend가 발생하면 JES가 후속 스텝을 스킵하고 잡 로그에 S999 덤프를 남긴다.

### 8. 이 잡의 현대화 위치
READACCT는 운영 배치가 아닌 **개발/검증 유틸리티**이다. Java 마이그레이션 관점에서는 다음으로 대체된다:
- OUTFILE 출력 → JPA Repository의 `findAll()` + CSV/JSON 직렬화
- ARRYFILE 출력 → 단위 테스트용 픽스처 데이터 생성기
- VBRCFILE 출력 → VB 포맷 파싱 라이브러리 검증용 테스트 데이터

야간 배치 시퀀스와 무관하므로 마이그레이션 우선순위는 POSTTRAN, INTCALC 등 핵심 배치보다 낮다.
