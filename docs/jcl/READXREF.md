# READXREF — 카드 교차참조 파일 읽기·덤프 잡

- **유형**: JCL (단일 스텝 배치 잡)
- **한 줄 요약**: CARDXREF VSAM KSDS를 순차 스캔하여 전체 교차참조 레코드(카드번호↔고객ID↔계정ID)를 SYSOUT으로 덤프하는 진단·검증용 잡

---

## 기능 설명

READXREF는 CardDemo의 카드/계정/고객 교차참조 마스터 파일인
`AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS`를 처음부터 끝까지 순차로 읽어,
각 레코드를 SYSOUT(JES 스풀)에 그대로 출력한다.

실행 주체 프로그램 `CBACT03C`는 다음 논리를 수행한다.

1. `0000-XREFFILE-OPEN`: XREFFILE-FILE을 INPUT으로 OPEN. 실패 시 CEE3ABD로 abend.
2. `PERFORM UNTIL END-OF-FILE = 'Y'` 루프: 매 반복마다 `1000-XREFFILE-GET-NEXT`를 호출.
3. `1000-XREFFILE-GET-NEXT`: `READ XREFFILE-FILE INTO CARD-XREF-RECORD` 실행.
   - FILE STATUS `'00'` → APPL-RESULT=0(APPL-AOK), 레코드를 DISPLAY 출력.
   - FILE STATUS `'10'` → EOF, APPL-RESULT=16(APPL-EOF), END-OF-FILE='Y' 세팅.
   - 그 외 → 오류 메시지 + `9910-DISPLAY-IO-STATUS` + abend.
4. 루프 종료 후 `9000-XREFFILE-CLOSE`로 파일 닫기.
5. GOBACK으로 정상 종료(RETURN-CODE 미설정 = 0).

> **주의**: `1000-XREFFILE-GET-NEXT` 안에서 `DISPLAY CARD-XREF-RECORD`가 두 번
> 호출된다 — READ 직후(96행)와 루프 본체(78행). 즉 EOF가 아닌 정상 레코드는
> 스풀에 중복 출력된다. `CBACT03C.CBL` 96행·78행 참조. 이는 버그로 추정되며,
> 프로그램 목적이 "읽기 검증 진단"이므로 실운영 중복 출력이 허용된 것으로 보인다.

이 잡은 야간 배치 시퀀스(POSTTRAN → INTCALC → TRANBKP 등)의 일부가 아닌
**독립 진단·개발 검증용 잡**이다. 운영자가 CARDXREF 파일의 내용을 육안으로
확인하거나, 데이터 로드 후 정합성을 점검할 때 임의로 제출한다.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| `STEP05` | `PGM=CBACT03C` | CARDXREF KSDS 전체 순차 스캔 후 레코드를 SYSOUT으로 덤프 |

스텝은 1개뿐이며 PROC 호출 없이 PGM 직접 실행이다.

### STEP05 DD 구성

| DD명 | 데이터셋 / 라우팅 | 설명 |
|------|------------------|------|
| `STEPLIB` | `AWS.M2.CARDDEMO.LOADLIB` (SHR) | CBACT03C 로드 모듈 라이브러리 |
| `XREFFILE` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` (SHR) | 카드 교차참조 VSAM KSDS (읽기 전용) |
| `SYSOUT` | `SYSOUT=*` | 프로그램 DISPLAY 출력 → JES 스풀 |
| `SYSPRINT` | `SYSOUT=*` | 시스템/런타임 메시지 → JES 스풀 |

`DISP=SHR`은 VSAM 파일을 공유 모드로 열어 CICS 온라인 영역이 동시에 파일을
점유 중이어도 잡이 읽기를 시도할 수 있게 한다. 단, VSAM KSDS에 대한 SHR 동시
접근은 CICS 측 파일을 먼저 닫지 않으면 ENQ 충돌이 발생할 수 있다(실환경에서는
CLOSEFIL 잡 실행 후 수행 권장).

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음. 이 JCL은 외부 PROC이나 JCLLIB INCLUDE를 참조하지 않는다.

- **호출 프로그램 (EXEC PGM)**:
  - `CBACT03C` — `app/cbl/CBACT03C.cbl`에 위치한 배치 COBOL 프로그램.
    - 내부에서 `COPY CVACT03Y`를 통해 `CARD-XREF-RECORD` 레이아웃(카드번호 16B + 고객ID 9B + 계정ID 11B + FILLER 14B = 50B)을 WORKING-STORAGE에 포함.
    - I/O 오류 시 IBM Language Environment 런타임 루틴 `CEE3ABD`를 CALL하여 user abend code 999로 강제 종료.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.LOADLIB` — 컴파일·링크된 로드 모듈 PDS. CBACT03C 실행 파일 포함.
  - `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` — 카드 교차참조 VSAM KSDS.
    - 레코드 길이 50바이트, 기본 키(XREF-CARD-NUM, PIC X(16)).
    - `app/cpy/CVACT03Y.cpy`의 `CARD-XREF-RECORD`가 정규 레이아웃.
    - XREF-CARD-NUM(16B) + XREF-CUST-ID PIC 9(09)(9B) + XREF-ACCT-ID PIC 9(11)(11B) + FILLER(14B).
    - CBTRN02C(POSTTRAN)·CBACT04C(INTCALC)·CBTRN03C(TRANREPT) 등 다수 배치 프로그램이 RANDOM 접근 모드로 이 파일을 조회한다.
    - DB2/SQL 없음 — 순수 VSAM 파일 기반.

- **선행/후행 잡**:
  - 공식 야간 배치 시퀀스(CLOSEFIL → ... → OPENFIL)에 포함되지 않는 독립 진단 잡이므로 정해진 선행·후행 잡이 없다.
  - CICS가 CARDXREF를 열어 두고 있는 환경이라면, 실행 전 `CLOSEFIL.jcl`을 먼저 제출하여 CICS 측 파일을 닫아야 ENQ 경합을 피할 수 있다(추측).
  - 유사 진단 잡: `READACCT.jcl`(CBACT01C, 계정 파일), `READCARD.jcl`(CBACT02C, 카드 파일), `READCUST.jcl`(CBCUS01C, 고객 파일) — 모두 동일한 패턴.

---

## Java/현대화 노트

### 개념 매핑

| JCL/COBOL 구성 | Java 현대화 대응 |
|---------------|-----------------|
| `JOB` 스테이트먼트 | `main()` 진입점 또는 Spring Batch `Job` 빈 |
| `EXEC PGM=CBACT03C` | `new Cbact03cTasklet()` 실행, 또는 `@Component` 태스크릿 |
| `DD XREFFILE` | `Resource` 주입 / VSAM → 관계형 DB의 경우 `JdbcCursorItemReader` |
| `DD SYSOUT=*` | `System.out.println()` 또는 `Slf4j` 로거 |
| `DISP=SHR` | 읽기 전용 DB 커넥션 / `@Transactional(readOnly=true)` |
| `CEE3ABD` 호출 | `throw new RuntimeException(...)` 또는 `System.exit(999)` |

### CARD-XREF-RECORD → Java DTO

```java
// app/cpy/CVACT03Y.cpy 매핑
public class CardXrefRecord {
    // PIC X(16) — 카드번호, EBCDIC → ASCII 변환 필요
    private String xrefCardNum;   // 16자 고정길이 String

    // PIC 9(09) — DISPLAY 형식 9자리 정수
    private long xrefCustId;      // long (최대 999,999,999)

    // PIC 9(11) — DISPLAY 형식 11자리 정수
    private long xrefAcctId;      // long (최대 99,999,999,999)

    // FILLER PIC X(14) — 14바이트 패딩, 무시
}
```

- `PIC 9(09)`와 `PIC 9(11)`은 부호 없는 DISPLAY(EBCDIC 존 10진수) 정수다.
  `int` 범위(약 21억)를 초과할 수 있으므로 `long`으로 매핑한다.
- 고정 레코드 길이 50바이트: EBCDIC 환경에서 각 문자는 1바이트. ASCII 환경에서도
  1바이트이므로 오프셋 이동 없음. 단 EBCDIC 코드페이지(Cp037 등)로 읽은 후
  변환해야 한다.

### 중복 DISPLAY 버그

`CBACT03C.cbl` 96행과 78행에서 동일 레코드를 두 번 DISPLAY한다.
Java로 변환 시 이 동작을 그대로 재현하지 말고, READ 후 단일 출력으로 정리할 것.

```java
// COBOL 버그 패턴 (재현 금지)
// 96행: DISPLAY CARD-XREF-RECORD  ← 1000-XREFFILE-GET-NEXT 내부
// 78행: DISPLAY CARD-XREF-RECORD  ← 루프 본체

// 올바른 Java 변환
while ((record = reader.readNext()) != null) {
    log.info("{}", record);  // 한 번만 출력
}
```

### Spring Batch 마이그레이션 패턴

이 잡의 기능(파일 전체 덤프)은 Spring Batch의
`FlatFileItemReader` + `ItemWriter<CardXrefRecord>` 조합으로 직접 대응된다.
다만 원본이 VSAM KSDS인 경우 관계형 DB로 마이그레이션한다면
`JdbcCursorItemReader`를 사용하고, VSAM 에뮬레이션 환경에서는
AWS Mainframe Modernization의 VSAM 커넥터 어댑터를 활용한다.

```java
@Bean
public Job readXrefJob(JobRepository jobRepository, Step step) {
    return new JobBuilder("READXREF", jobRepository)
        .start(step)
        .build();
}

@Bean
public Step readXrefStep(JobRepository jobRepository,
                          PlatformTransactionManager tm,
                          ItemReader<CardXrefRecord> reader,
                          ItemWriter<CardXrefRecord> writer) {
    return new StepBuilder("STEP05", jobRepository)
        .<CardXrefRecord, CardXrefRecord>chunk(100, tm)
        .reader(reader)   // VSAM KSDS → JdbcCursorItemReader
        .writer(writer)   // DISPLAY → log.info 또는 파일 출력
        .build();
}
```

### 주의 사항

- **DISP=SHR와 CICS 동시 접근**: VSAM 파일을 CICS가 열어 두고 있으면
  `DISP=SHR`이어도 배치가 VSAM 내부 RLS(Record Level Sharing) 미설정 환경에서
  ENQ 경합을 일으킬 수 있다. 현대화 시 DB화하면 이 문제는 사라진다.
- **SYSOUT 덤프 한계**: 레코드 수가 수백만 건이면 JES 스풀이 가득 찰 수 있다.
  현대화 시 페이지네이션 조회 또는 별도 파일 출력으로 대체 권장.
- **CEE3ABD**: IBM LE(Language Environment) 전용 abend 루틴으로
  z/OS 외 환경에서는 동작하지 않는다. Java 이식 시 예외 처리로 대체.

---

*소스 버전: `CBACT03C.CBL` — CardDemo_v2.0-25-gdb72e6b-235 (2025-04-29)*
*copybook: `CVACT03Y.cpy` — CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19)*
