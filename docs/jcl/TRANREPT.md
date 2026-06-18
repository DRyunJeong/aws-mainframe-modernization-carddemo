# TRANREPT — 거래 상세 리포트 생성 잡

- **유형**: JCL (배치 잡)
- **한 줄 요약**: VSAM 거래 파일을 순차 백업으로 언로드한 뒤, 날짜 범위로 필터링·카드번호순 정렬하고, CBTRN03C를 실행해 계정 단위 합계가 포함된 133바이트 인쇄 리포트를 생성한다.

---

## 기능 설명

TRANREPT 잡은 세 단계로 이루어진 **"추출 → 필터/정렬 → 리포트"** 파이프라인이다.

1. **VSAM 언로드** (`STEP05R` / REPROC PROC): IDCAMS REPRO 유틸리티가 VSAM KSDS 거래 파일 전체를 세대 데이터 그룹(GDG) 백업 데이터셋으로 복사한다. VSAM은 임의 접근 구조이므로, 순차적으로 읽고 쓰는 SORT나 배치 프로그램에 넘기기 전에 반드시 순차 평탄(flat) 파일로 변환해야 한다. 이 단계가 그 역할을 한다.

2. **날짜 필터 및 정렬** (`STEP05R` / SORT): IBM DFSORT(또는 SYNCSORT)가 백업 파일에서 처리일자(`TRAN-PROC-DT`, 오프셋 305, 길이 10)가 파라미터 날짜 범위(하드코딩 `2022-01-01` ~ `2022-07-06`)에 해당하는 레코드만 INCLUDE 조건으로 추출하고, 카드번호(`TRAN-CARD-NUM`, 오프셋 263, 길이 16) 오름차순으로 정렬하여 일별 작업 데이터셋(DALY GDG)에 저장한다. SORT의 SYMNAMES 기능으로 오프셋 숫자 대신 의미 있는 이름(`TRAN-CARD-NUM`, `TRAN-PROC-DT`)을 사용하고 있다.

3. **리포트 출력** (`STEP10R` / CBTRN03C): COBOL 배치 프로그램 CBTRN03C가 정렬된 DALY 파일을 드라이빙 파일로 읽으며, CARDXREF·TRANTYPE·TRANCATG 세 VSAM KSDS를 RANDOM READ로 룩업 조인한다. DATEPARM 파라미터 파일에서 날짜 범위를 다시 읽어 2차 필터를 수행하고, 카드번호 기준 control-break 소계와 전체 합계, 20줄 단위 페이지네이션을 포함한 133바이트 고정 폭 인쇄 리포트를 출력한다.

**주목해야 할 설계 포인트**: JCL 내에 `STEP05R`라는 스텝명이 두 번 나온다(23행 PROC 호출, 37행 SORT). 이는 JCL의 오기(중복 스텝명)로, 메인프레임 JES2/JES3는 통상 두 번째 정의를 우선하거나 오류로 처리한다(추측 — 실제 동작은 JES 버전에 따라 다를 수 있으므로 실환경 검증 필요). `TRANREPT.prc`를 보면 이 잡의 전체 내용이 사실상 PROC로도 패키징되어 있어, 잡 자체가 PROC의 인라인 전개(inline expansion)로 작성된 것으로 보인다.

---

## 스텝 구성

| 스텝명 | EXEC PGM/PROC | 역할 |
|--------|--------------|------|
| `STEP05R` (첫 번째, JCL 23행) | `PROC=REPROC` | VSAM KSDS → 순차 GDG 백업 언로드 (IDCAMS REPRO). PROC 내부 스텝명은 `PRC001`. `CNTLLIB=AWS.M2.CARDDEMO.CNTL`에서 `REPROCT` 멤버(REPRO 제어문)를 읽음. FILEIN/FILEOUT은 JCL에서 오버라이드. |
| `STEP05R` (두 번째, JCL 37행) | `PGM=SORT` | GDG 백업에서 날짜 범위 레코드 INCLUDE 필터 후 카드번호 오름차순 정렬 → DALY GDG 출력. SYMNAMES로 필드 이름 지정. |
| `STEP10R` (JCL 59행) | `PGM=CBTRN03C` | 정렬된 DALY 파일 + VSAM 룩업 3개 + DATEPARM → 133바이트 인쇄 리포트(TRANREPT GDG) 출력. |

---

## 의존성

### COPY (PROC/INCLUDE)
- `PROC=REPROC` — `app/proc/REPROC.prc` (IDCAMS REPRO 래퍼 PROC)
  - 내부에서 `&CNTLLIB(REPROCT)` = `AWS.M2.CARDDEMO.CNTL(REPROCT)` 멤버를 SYSIN으로 읽음 (`app/ctl/REPROCT.ctl`: `REPRO INFILE(FILEIN) OUTFILE(FILEOUT)`)
- `TRANREPT.prc` (`app/proc/TRANREPT.prc`) — 이 잡의 내용을 PROC으로 캡슐화한 동명 PROC. 내용이 본 JCL과 사실상 동일하므로 재사용 시 PROC 형태로 호출 가능(추측).

### 호출 프로그램 (EXEC PGM)
| 프로그램 | 위치 | 설명 |
|---------|------|------|
| `IDCAMS` | 시스템 유틸리티 | VSAM REPRO — KSDS를 순차 파일로 복사 |
| `SORT` | 시스템 유틸리티 | IBM DFSORT / SYNCSORT — 필터·정렬 |
| `CBTRN03C` | `AWS.M2.CARDDEMO.LOADLIB` | COBOL 리포트 생성 배치 (`app/cbl/CBTRN03C.cbl`). DATEPARM 일자 필터 + 4파일 RANDOM 룩업 조인 + control-break 소계 + 페이지네이션. |

### 데이터셋/파일/DB 테이블

| DD명 | 데이터셋명 | 유형 | 방향 | 설명 |
|------|-----------|------|------|------|
| `PRC001.FILEIN` | `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS` | VSAM KSDS | 입력 | 원본 거래 VSAM 파일 (REPROC 언로드 소스) |
| `PRC001.FILEOUT` | `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` | GDG / 순차 FB LRECL=350 | 출력 | VSAM 언로드 결과 백업 GDG 신규 세대 |
| `SORTIN` | `AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)` | 순차 | 입력 | 위에서 생성한 백업 — SORT 입력 |
| `SORTOUT` | `AWS.M2.CARDDEMO.TRANSACT.DALY(+1)` | GDG / 순차 FB (DCB=*.SORTIN) | 출력 | 필터·정렬된 일별 작업 GDG 신규 세대 |
| `TRANFILE` | `AWS.M2.CARDDEMO.TRANSACT.DALY(+1)` | 순차 | 입력 | CBTRN03C 드라이빙 파일 (정렬된 거래) |
| `CARDXREF` | `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS` | VSAM KSDS | 입력 (RANDOM) | 카드번호 → 계정ID 크로스레퍼런스 룩업 |
| `TRANTYPE` | `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS` | VSAM KSDS | 입력 (RANDOM) | 거래 유형코드 → 유형 설명 룩업 |
| `TRANCATG` | `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS` | VSAM KSDS | 입력 (RANDOM) | 유형+카테고리코드 → 카테고리 설명 룩업 |
| `DATEPARM` | `AWS.M2.CARDDEMO.DATEPARM` | 순차 (1레코드) | 입력 | 리포트 날짜 범위 파라미터 (CBTRN03C의 2차 필터용) |
| `TRANREPT` | `AWS.M2.CARDDEMO.TRANREPT(+1)` | GDG / 순차 FB LRECL=133 | 출력 | 최종 인쇄 리포트 GDG 신규 세대 |

### 선행/후행 잡

- **선행 잡**: `POSTTRAN` (CBTRN02C) — 일별 거래를 검증·포스팅하여 `TRANSACT.VSAM.KSDS`에 기록함. TRANREPT 잡은 이 파일을 소스로 사용하므로 반드시 POSTTRAN 완료 후 실행해야 한다.
- **선행 잡(선택)**: `TRANBKP` — 이미 `TRANSACT.BKUP` GDG를 생성하는 별도 백업 잡이 야간 배치 시퀀스에 존재함. TRANREPT의 STEP05R(REPROC)와 역할이 중복될 수 있으므로 운영 환경에서 잡 순서 확인 필요(추측).
- **후행 잡**: 명시적 후행 잡 없음. 생성된 `TRANREPT(+1)` GDG를 FTP/출력 큐로 배포하는 별도 잡이 존재할 수 있음(추측).
- **CICS 연계**: CBTRN03C는 온라인 프로그램 `COTRN00C`/`COTRN01C`/`COTRN02C`(거래 조회/추가)가 기록한 VSAM 데이터를 사후 배치 리포팅하는 용도이므로, CICS에서 직접 이 잡을 SUBMIT하는 경로는 베이스 코드에 확인되지 않음.

---

## Java/현대화 노트

### 1. GDG(세대 데이터 그룹) → 버전 관리 스토리지

JCL 곳곳에서 `DSN=...BKUP(+1)`, `...DALY(+1)`, `...TRANREPT(+1)`처럼 `(+1)` 표기가 등장한다(JCL 33행, 55행, 80행). 이것이 GDG의 신규 세대 생성 표기다. Java 현대화 시 대응 패턴:

```java
// GDG (+1) ≈ 새 버전의 파일/레코드 생성
// 옵션 A: 날짜 접미사 파일명
String backupPath = "TRANSACT.BKUP." + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

// 옵션 B: 오브젝트 스토리지(S3) 버전 관리 활성화
// s3://bucket/TRANSACT/BKUP/ — S3 versioning이 GDG 세대 보존과 동일 역할
```

### 2. IDCAMS REPRO → Java InputStream/OutputStream 복사

REPROC PROC의 핵심은 IDCAMS `REPRO INFILE(FILEIN) OUTFILE(FILEOUT)` 한 줄(`app/ctl/REPROCT.ctl` 15행)이다. VSAM을 순차 파일로 복사하는 이 패턴은 Java에서 단순 스트림 복사에 해당한다:

```java
// IDCAMS REPRO ≈ 레코드 단위 복사
try (InputStream in = vsam.openSequential("TRANSACT.VSAM.KSDS");
     OutputStream out = Files.newOutputStream(backupPath)) {
    in.transferTo(out);  // 전체 레코드셋 복사
}
```

현대화 환경에서는 VSAM을 RDB/NoSQL로 대체했다면 이 언로드 단계 자체가 불필요해지며, JDBC SELECT → CSV/Parquet export로 대체된다.

### 3. SORT INCLUDE/SYMNAMES → Stream filter/sorted

SORT 스텝(37~55행)의 SYMNAMES 블록은 레코드 내 바이트 오프셋에 의미 있는 이름을 붙이는 것으로, COBOL 레코드의 PIC 필드 정의와 동일한 역할이다:

```
TRAN-CARD-NUM,263,16,ZD   ← 오프셋 263, 길이 16, Zoned Decimal
TRAN-PROC-DT,305,10,CH    ← 오프셋 305, 길이 10, Character
```

Java 대응:

```java
// SORT FIELDS=(TRAN-CARD-NUM,A) + INCLUDE COND=(날짜 범위)
List<Transaction> filtered = transactions.stream()
    .filter(t -> !t.getProcDate().isBefore(startDate)
              && !t.getProcDate().isAfter(endDate))
    .sorted(Comparator.comparing(Transaction::getCardNumber))
    .collect(Collectors.toList());
```

주의: `ZD`(Zoned Decimal)는 EBCDIC 존 십진수 형식으로, ASCII 환경의 숫자 문자열과 다르다. `TRAN-CARD-NUM`이 ZD로 선언되어 있으나 카드번호는 통상 순수 수치가 아닌 문자열로 취급하는 것이 안전하다.

### 4. 날짜 하드코딩 문제

SORT 스텝의 SYMNAMES에 날짜가 하드코딩되어 있다(JCL 43~44행):

```
PARM-START-DATE,C'2022-01-01'
PARM-END-DATE,C'2022-07-06'
```

실제로는 DATEPARM 데이터셋(`AWS.M2.CARDDEMO.DATEPARM`)이 CBTRN03C에 파라미터를 제공하는데, SORT 스텝의 날짜는 별도로 하드코딩되어 있어 양쪽이 일치하지 않으면 SORT 단계에서 포함된 레코드와 CBTRN03C의 필터 결과가 다를 수 있다. Java 현대화 시 날짜 파라미터는 단일 설정 소스(예: Spring Batch `JobParameters`)에서 관리해야 한다:

```java
// Spring Batch JobParameters로 날짜 범위 주입
@Bean
public Job tranReportJob(JobBuilderFactory jobs, Step filterStep, Step reportStep) {
    return jobs.get("tranReportJob")
        .start(filterStep)
        .next(reportStep)
        .build();
}
// JobLauncher.run(job, new JobParametersBuilder()
//     .addString("startDate", "2022-01-01")
//     .addString("endDate", "2022-07-06")
//     .toJobParameters());
```

### 5. LRECL=133 인쇄 리포트 → 현대적 리포트 출력

출력 `TRANREPT` DD의 `DCB=(LRECL=133,RECFM=FB)`(JCL 78행)는 전통적인 메인프레임 프린터 폭(132컬럼 + 1바이트 ASA 캐리지 제어문자)이다. CBTRN03C의 리포트 레이아웃 copybook `CVTRA07Y`가 이 133바이트 레이아웃을 정의한다. Java 현대화 시 선택지:

- 텍스트 그대로 유지: `PrintWriter`로 132자 고정폭 출력 → `.txt` 또는 스풀 시스템
- 구조화 출력: JasperReports / Apache POI(Excel) / PDF(iText)로 대체
- 마이크로서비스: REST API로 리포트 데이터 노출 후 프론트엔드에서 렌더링

### 6. STEP05R 중복 스텝명 (잠재적 JCL 버그)

JCL 23행과 37행에 `STEP05R`가 두 번 정의되어 있다. JES2 환경에서는 이를 오류로 처리하거나(JCLERROR) 두 번째 정의가 첫 번째를 덮어쓸 수 있다(추측 — 실환경 JES 버전 의존). `TRANREPT.prc`에서는 첫 번째 PROC 호출 스텝이 `STEP01R`로 정의되어 있어, 본 JCL의 `STEP05R` 중복은 오기일 가능성이 높다. 현대화 시 파이프라인 스텝명은 고유하게 부여해야 한다.

### 7. control-break 패턴 → Spring Batch Chunk/Listener

CBTRN03C의 카드번호 기준 소계(`control-break`) 패턴은 Spring Batch의 `ItemWriteListener` 또는 `StepExecutionListener`로 구현한다:

```java
// control-break ≈ Spring Batch GroupingItemWriter 또는 커스텀 ItemWriteListener
public class CardBreakListener implements ItemWriteListener<TransactionReport> {
    private String lastCardNumber = null;
    private BigDecimal cardTotal = BigDecimal.ZERO;

    @Override
    public void afterWrite(List<? extends TransactionReport> items) {
        for (TransactionReport item : items) {
            if (!item.getCardNumber().equals(lastCardNumber)) {
                if (lastCardNumber != null) printCardSubtotal(lastCardNumber, cardTotal);
                cardTotal = BigDecimal.ZERO;
                lastCardNumber = item.getCardNumber();
            }
            cardTotal = cardTotal.add(item.getAmount());  // BigDecimal: S9(9)V99 대응
        }
    }
}
```

`TRAN-AMT` 합산 필드는 CBTRN03C에서 `S9(09)V99` COMP-3으로 선언되므로 반드시 `BigDecimal(scale=2)`로 매핑해야 `double`/`float`의 부동소수점 오차를 피할 수 있다.
