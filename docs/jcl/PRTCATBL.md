# PRTCATBL — 거래 카테고리 잔액 파일 언로드 및 정렬 출력

- **유형**: JCL (배치 유틸리티 잡)
- **한 줄 요약**: VSAM KSDS에 보관된 거래 카테고리 잔액(TCATBALF)을 순차 백업으로 언로드한 뒤, 계정ID·거래유형·카테고리 코드 순으로 정렬하여 읽기 쉬운 고정 포맷 보고서를 생성하는 유틸리티 잡.

---

## 기능 설명

PRTCATBL은 야간 배치 처리 후 `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS`(KSDS VSAM)에 쌓인 거래 카테고리 잔액 레코드를 사람이 읽을 수 있는 형태로 꺼내 보는 진단·감사 목적의 잡이다.

처리 흐름은 크게 세 단계로 구성된다.

1. **사전 삭제(DELDEF)**: 이전 실행에서 남은 보고서 데이터셋(`TCATBALF.REPT`)을 `IEFBR14`로 조건부 삭제하여 다음 스텝이 `(NEW,CATLG)` 조건으로 깨끗하게 생성할 수 있도록 정리한다.
2. **VSAM → 순차 언로드(STEP05R)**: IDCAMS REPRO로 KSDS를 고정 블록(FB, LRECL=50) 백업 데이터셋 `TCATBALF.BKUP(+1)`에 복사한다. GDG(Generation Data Group) 패턴(`(+1)`)을 사용하므로 매 실행마다 세대가 증가하며 이전 실행분이 보존된다.
3. **정렬 + 편집 출력(STEP10R)**: IBM SORT(DFSORT 또는 SyncSort)로 언로드된 순차 파일을 정렬하고, `OUTREC`으로 각 필드 사이에 공백 구분자(`X`)를 삽입하고 잔액을 `EDIT=(TTTTTTTTT.TT)` 마스크로 십진 소수점 편집하여 LRECL=40 보고서 파일을 생성한다.

레코드 레이아웃은 copybook `CVTRA01Y.cpy`의 `TRAN-CAT-BAL-RECORD`(총 50바이트)를 따른다.

---

## 스텝 구성

| 스텝명   | EXEC PGM/PROC          | 역할                                                                 |
|----------|------------------------|----------------------------------------------------------------------|
| DELDEF   | `PGM=IEFBR14`          | `TCATBALF.REPT` 데이터셋을 `(MOD,DELETE)`로 조건부 삭제. IEFBR14는 아무 처리도 하지 않으며, DD 카드의 DISP 처리만 JCL 시스템이 수행한다. |
| STEP05R  | `PROC=REPROC`          | REPROC 카탈로그 프로시저 내 `IDCAMS REPRO`를 호출해 VSAM KSDS를 FB/LRECL=50 GDG 백업 데이터셋으로 언로드한다. SYSIN은 `CNTLLIB(REPROCT)` 멤버(`REPRO INFILE(FILEIN) OUTFILE(FILEOUT)`)가 제공한다. |
| STEP10R  | `PGM=SORT`             | 언로드 파일을 `TRANCAT-ACCT-ID` 오름차순 → `TRANCAT-TYPE-CD` 오름차순 → `TRANCAT-CD` 오름차순으로 정렬하고, `OUTREC`으로 필드를 재배치·편집하여 LRECL=40 보고서를 생성한다. |

---

## 의존성

### COPY (PROC/INCLUDE)

- `PROC=REPROC` — `app/proc/REPROC.prc`. `PRC001` 스텝에서 `PGM=IDCAMS`를 실행하고 `FILEIN`/`FILEOUT` DD는 호출 JCL에서 오버라이드된다.
- `CNTLLIB=AWS.M2.CARDDEMO.CNTL` — REPROC 프로시저의 SYSIN을 제공하는 PARTITIONED DATA SET(PDS). `REPROCT` 멤버(`app/ctl/REPROCT.ctl`)에 `REPRO INFILE(FILEIN) OUTFILE(FILEOUT)` 한 줄의 IDCAMS 제어문이 들어 있다.
- `JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')` (라인 19) — REPROC 프로시저가 위치하는 프로시저 라이브러리.

### 호출 프로그램 (EXEC PGM)

- `IEFBR14` (DELDEF 스텝) — IBM 제공 더미 유틸리티. 실행 내용 없음; JCL DD DISP 처리를 위한 관용적 패턴.
- `IDCAMS` (REPROC 프로시저 내 PRC001 스텝) — IBM Access Method Services. `REPRO` 명령으로 VSAM KSDS를 순차 파일로 언로드.
- `SORT` (STEP10R) — IBM DFSORT 또는 SyncSort. `SORT FIELDS`/`OUTREC`/`SYMNAMES` 제어문으로 정렬 및 레코드 편집 수행.

### 데이터셋/파일/DB 테이블

| 데이터셋 이름                              | 유형              | 역할                                    | 접근 |
|--------------------------------------------|-------------------|-----------------------------------------|------|
| `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS`      | VSAM KSDS         | 소스: 거래 카테고리 잔액 마스터 파일     | 읽기 (DISP=SHR) |
| `AWS.M2.CARDDEMO.TCATBALF.BKUP(+1)`       | GDG 순차(FB/50)   | 중간: VSAM 언로드 백업 (세대별 보존)    | 쓰기 (NEW,CATLG) |
| `AWS.M2.CARDDEMO.TCATBALF.REPT`           | 순차(FB/40)       | 최종 출력: 정렬·편집된 보고서 파일      | 쓰기 (NEW,CATLG) |
| `AWS.M2.CARDDEMO.CNTL(REPROCT)`           | PDS 멤버          | IDCAMS REPRO 제어문 (`app/ctl/REPROCT.ctl`) | 읽기 |

**레코드 레이아웃** (`CVTRA01Y.cpy` — `TRAN-CAT-BAL-RECORD`, 50바이트):

| 필드명              | SYMNAMES 정의                  | PIC 정의              | 오프셋 | 길이 | Java 대응                        |
|---------------------|--------------------------------|-----------------------|--------|------|----------------------------------|
| `TRANCAT-ACCT-ID`   | offset 1, len 11, type ZD      | `PIC 9(11)`           | 1      | 11   | `String` (11자리 계정ID)         |
| `TRANCAT-TYPE-CD`   | offset 12, len 2, type CH      | `PIC X(02)`           | 12     | 2    | `String` (거래 유형 코드)        |
| `TRANCAT-CD`        | offset 14, len 4, type ZD      | `PIC 9(04)`           | 14     | 4    | `int` (카테고리 코드)            |
| `TRAN-CAT-BAL`      | offset 18, len 11, type ZD     | `PIC S9(09)V99`       | 18     | 11   | `BigDecimal` (부호+소수점 2자리) |
| `FILLER`            | —                              | `PIC X(22)`           | 29     | 22   | (미사용 패딩)                    |

> **주의**: SYMNAMES에서 `TRAN-CAT-BAL`의 type을 `ZD`(Zoned Decimal)로 선언했지만, copybook의 원 정의는 `PIC S9(09)V99`(부호 포함 11자리 내재적 소수점). VSAM KSDS에서 DISPLAY 형식으로 저장된 경우 ZD 처리가 맞으나, COMP-3으로 저장되었다면 SYMNAMES 정의가 잘못된 것이다. 이 코드베이스의 CBTRN02C 및 CBACT04C는 TCATBALF를 DISPLAY USAGE로 처리하므로 ZD 선언은 올바르다고 판단됨.

### 선행/후행 잡

- **선행 잡**: 야간 배치 시퀀스에서 TCATBALF를 갱신하는 `POSTTRAN`(CBTRN02C) 및 `INTCALC`(CBACT04C) 잡이 완료된 후 실행되어야 한다. PRTCATBL 자체는 VSAM 파일을 갱신하지 않으므로 CLOSEFIL/OPENFIL 사이에 끼워 넣을 필요는 없다(DISP=SHR로 읽기만 함). 다만 CICS가 해당 VSAM을 OPEN 중이라면 SHR 접근이 경합할 수 있으므로, 실제 환경에서는 CLOSEFIL 이후에 실행하는 것이 안전하다(추측).
- **후행 잡**: 없음. 이 잡의 출력(`TCATBALF.REPT`, `TCATBALF.BKUP(+1)`)은 다른 잡의 입력으로 직접 사용되지 않으며, 운영자 확인 또는 감사 보관 목적으로 사용된다.

---

## Java/현대화 노트

### IEFBR14 삭제 패턴 → Java NIO

```java
// DELDEF 스텝의 IEFBR14 + (MOD,DELETE) 패턴
// 메인프레임에서는 DD DISP=(MOD,DELETE)가 파일이 있으면 삭제, 없어도 오류 없음
Path reportFile = Paths.get("/data/tcatbalf-report.dat");
Files.deleteIfExists(reportFile); // Java NIO 등가 패턴
```

### IDCAMS REPRO → Java InputStream/OutputStream 복사

```java
// VSAM KSDS "언로드"는 실제로는 레코드 단위 순차 읽기 → 순차 파일 쓰기
// Java에서는 VSAM 대신 일반적으로 데이터베이스 → 파일 내보내기로 대체됨
try (InputStream vsam = new VsamKsdsInputStream("TCATBALF");
     OutputStream backup = new FileOutputStream("tcatbalf_backup_gen" + generation)) {
    vsam.transferTo(backup); // Java 9+
}
```

### GDG(+1) 세대 관리 → Java 날짜/버전 파일명

GDG `BKUP(+1)` 패턴은 매 실행마다 새 세대를 만들어 이전 세대를 자동 보존한다. Java 현대화 시에는 파일명에 타임스탬프 또는 실행 순번을 붙이는 방식으로 대체한다:

```java
String backupName = "tcatbalf_backup_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".dat";
```

### SORT OUTREC 편집 → Java String.format / BigDecimal

SORT STEP10R의 `OUTREC`은 두 가지 편집을 수행한다.

1. **`X` 구분자 삽입**: 필드 사이에 단일 공백을 넣어 사람이 읽기 쉬운 형태로 만듦.
2. **`EDIT=(TTTTTTTTT.TT)` 마스크**: `TRAN-CAT-BAL`을 9자리 정수부 + 소수점 + 2자리 소수부 형태의 문자열로 변환.

```java
// TRAN-CAT-BAL: PIC S9(09)V99 → BigDecimal(scale=2)
BigDecimal balance = new BigDecimal("12345678.99"); // 내재적 소수점 V99 적용 후

// OUTREC 재현: 필드 조합 + EDIT 마스크
String line = String.format("%-11s %-2s %-4s %12.2f%9s",
    tranCatAcctId, tranCatTypeCd, tranCatCd, balance, "");
// LRECL=40에 맞게 trim/pad 필요
```

> **정밀도 주의**: `TRAN-CAT-BAL`은 `S9(09)V99` — 부호 있는 9자리 정수 + 내재적 소수점 + 2자리 소수. Java `double`/`float`은 부동소수점 오차로 금융 계산에 부적합. 반드시 `BigDecimal`을 사용하고 `scale=2`, `RoundingMode.HALF_UP`을 명시한다.

### SYMNAMES DD — SORT에서의 필드 이름 지정

SORT 유틸리티 자체는 이름 없이 오프셋/길이로 필드를 처리한다. `SYMNAMES DD *` 블록은 DFSORT 전용 기능으로, 이름을 오프셋에 매핑해 `SORT FIELDS`/`OUTREC`에서 이름을 쓸 수 있게 한다. 이 파일에서의 정의는 앞서 제시한 `CVTRA01Y.cpy` 레코드 레이아웃과 일치한다(라인 47–50). Java로 마이그레이션할 때는 이 오프셋 정보가 레코드 파싱의 기준이 된다.

### 배치 잡 전체를 Spring Batch로 대체하는 경우

| JCL 스텝 | Spring Batch 대응 |
|----------|-------------------|
| DELDEF (IEFBR14) | `JobExecutionListener.beforeJob()` 내 파일 삭제 |
| STEP05R (IDCAMS REPRO) | `FlatFileItemWriter`로 DB/VSAM 조회 결과 직렬화; GDG → `MultiResourceItemWriter` 또는 롤링 파일명 전략 |
| STEP10R (SORT) | `SortedItemReader` 또는 쿼리 ORDER BY 절로 정렬 내재화; OUTREC 편집 → `ItemProcessor`의 필드 포맷팅 |

---

*소스 기준 라인: `app/jcl/PRTCATBL.jcl`, `app/proc/REPROC.prc`, `app/ctl/REPROCT.ctl`, `app/cpy/CVTRA01Y.cpy`*
