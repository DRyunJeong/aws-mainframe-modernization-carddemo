# DISCGRP — 디스클로저 그룹(이자율) VSAM 파일 초기 적재

- **유형**: JCL (배치 초기화 잡)
- **한 줄 요약**: 이자율 마스터인 공시 그룹(Disclosure Group) VSAM KSDS 파일을 삭제→재정의→순차 평면 파일로부터 복제하여 초기 적재한다.

---

## 기능 설명

`DISCGRP` 잡은 카드사의 **이자율 마스터 파일** `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS`를 처음 구축(또는 재초기화)하기 위한 3단계 파일 셋업 잡이다.

이자계산 배치(`CBACT04C`, 잡 `INTCALC`)가 야간에 각 거래 카테고리별 이자를 산출할 때 이 KSDS를 **읽기 전용 기준 테이블**로 참조한다. 레코드 하나가 `(계정그룹 ID, 거래유형코드, 거래카테고리코드)` 조합에 대한 **연이자율(%)**을 담으며, 그룹 ID `DEFAULT`는 전용 이자율이 없는 계정의 폴백(fallback) 행으로 사용된다.

Java에서의 대응 개념: **Flyway/Liquibase 마이그레이션 + 참조 데이터 초기 INSERT**와 동일한 역할이다. 애플리케이션 기동 전에 이자율 룩업 테이블을 준비해 두는 것이다.

---

## 스텝 구성

| 스텝명 | EXEC PGM | 역할 |
|--------|----------|------|
| STEP05 | `IDCAMS` | 기존 VSAM KSDS 클러스터 삭제; `SET MAXCC=0`으로 "이미 없는 경우" 오류를 무시 |
| STEP10 | `IDCAMS` | 새 VSAM KSDS 클러스터 정의 (키·레코드 크기·볼륨·공간 지정) |
| STEP15 | `IDCAMS` | 순차 평면 파일(`DISCGRP.PS`)의 레코드를 VSAM KSDS(`DISCGRP.VSAM.KSDS`)로 `REPRO` |

### STEP05 — DELETE (기존 클러스터 삭제)

```jcl
DELETE AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS CLUSTER
SET    MAXCC = 0
```

`IDCAMS DELETE`는 대상이 존재하지 않으면 RC=8을 반환한다. `SET MAXCC=0`은 그 RC를 강제로 0으로 낮춰 후속 스텝이 조건부 중지(`COND=...`) 없이 항상 실행되게 한다. Java 상당: `DROP TABLE IF EXISTS`와 동일한 보호 패턴.

### STEP10 — DEFINE CLUSTER (KSDS 정의)

```jcl
DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS)
       CYLINDERS(1 5)
       VOLUMES(AWSHJ1)
       KEYS(16 0)
       RECORDSIZE(50 50)
       SHAREOPTIONS(2 3)
       ERASE
       INDEXED)
DATA  (NAME(AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS.DATA))
INDEX (NAME(AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS.INDEX))
```

주요 파라미터 해석:

| 파라미터 | 값 | 의미 |
|----------|----|------|
| `CYLINDERS(1 5)` | 기본 1 실린더, 보조 5 실린더 | 초기 공간 할당; 필요 시 5실린더씩 자동 확장 |
| `KEYS(16 0)` | 키 길이 16바이트, 오프셋 0 | 레코드 맨 앞 16바이트가 VSAM 키 |
| `RECORDSIZE(50 50)` | 평균=최대=50바이트 | 고정 길이 레코드; Java `byte[50]` 배열에 해당 |
| `SHAREOPTIONS(2 3)` | 교차 지역 공유 옵션 | 다른 잡·CICS 리전과의 동시 접근 규칙 |
| `ERASE` | 삭제 시 물리 0-fill | 민감 데이터 보안 삭제 |

키 16바이트는 `CVTRA02Y` copybook의 `DIS-GROUP-KEY`에 정확히 대응한다:

```
DIS-GROUP-KEY (16 bytes)
  DIS-ACCT-GROUP-ID   PIC X(10)   — 계정 공시 그룹 ID (예: "DEFAULT")
  DIS-TRAN-TYPE-CD    PIC X(02)   — 거래 유형 코드
  DIS-TRAN-CAT-CD     PIC 9(04)   — 거래 카테고리 코드
```

### STEP15 — REPRO (평면 파일 → VSAM 복제)

```jcl
//DISCGRP  DD DISP=SHR, DSN=AWS.M2.CARDDEMO.DISCGRP.PS     ← 입력
//DISCVSAM DD DISP=OLD, DSN=AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS  ← 출력
REPRO INFILE(DISCGRP) OUTFILE(DISCVSAM)
```

`IDCAMS REPRO`는 순차 파일(`.PS` = Physical Sequential, QSAM)의 레코드를 VSAM KSDS로 한 건씩 삽입한다. Java 상당: `PreparedStatement`를 루프로 돌려 CSV를 DB에 `INSERT`하는 초기 데이터 로더.

입력 DD(`DISCGRP`)에 `DISP=SHR`을 사용하는 것은 원본 평면 파일이 다른 잡·CICS와 공유 상태임을 허용한다는 의미다.

---

## 의존성

### COPY (PROC/INCLUDE)

없음. 이 JCL은 PROC나 INCLUDE를 사용하지 않는다. `IDCAMS`는 IBM 시스템 유틸리티이므로 별도 컴파일이 불필요하다.

### 호출 프로그램 (EXEC PGM)

- `IDCAMS` — IBM AMS(Access Method Services) 유틸리티. VSAM 클러스터 정의·삭제·복제를 수행하는 IBM 제공 시스템 프로그램. 사용자 COBOL 프로그램을 호출하지 않는다.

### 데이터셋/파일/DB 테이블

| 데이터셋 이름 | 역할 | 조직 | 접근 |
|--------------|------|------|------|
| `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS` | 이자율 마스터 VSAM 클러스터 (출력) | VSAM KSDS | DELETE·DEFINE·WRITE (STEP05~15) |
| `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS.DATA` | 클러스터 데이터 컴포넌트 | VSAM | DEFINE (STEP10) |
| `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS.INDEX` | 클러스터 인덱스 컴포넌트 | VSAM | DEFINE (STEP10) |
| `AWS.M2.CARDDEMO.DISCGRP.PS` | 초기 적재 원본 순차 파일 | PS (QSAM) | READ (STEP15 DISCGRP DD) |

레코드 레이아웃 — copybook `CVTRA02Y` (`app/cpy/CVTRA02Y.cpy`):

```
01  DIS-GROUP-RECORD.                        총 50바이트
    05  DIS-GROUP-KEY.                       키 16바이트
       10 DIS-ACCT-GROUP-ID  PIC X(10)       계정그룹 ID (예: "DEFAULT   ")
       10 DIS-TRAN-TYPE-CD   PIC X(02)       거래유형 코드
       10 DIS-TRAN-CAT-CD    PIC 9(04)       거래카테고리 코드 (DISPLAY 숫자)
    05  DIS-INT-RATE          PIC S9(04)V99   연이자율 % (6바이트, DISPLAY)
    05  FILLER                PIC X(28)       예비 영역
```

`DIS-INT-RATE`의 `V99`는 소수점 위치만 묵시적으로 표시하는 COBOL 규약이다. 실제 저장 값이 `1250`이면 의미는 `12.50%`이다. Java 변환 시 반드시 `BigDecimal.movePointLeft(2)` 처리가 필요하다.

### 선행/후행 잡

| 구분 | 잡 이름 | 이유 |
|------|---------|------|
| 선행 (선택) | `CLOSEFIL` | CICS 리전이 이 파일을 잡고 있으면 DELETE가 실패한다. CICS 기동 중에 실행할 경우 먼저 VSAM을 닫아야 한다 (추측) |
| 후행 | `INTCALC` (CBACT04C) | 이자계산 배치가 DISCGRP KSDS를 랜덤 READ로 이자율을 조회한다 |
| 후행 | `OPENFIL` | DISCGRP를 CICS 리전에 재오픈하는 마스터 갱신 잡 시퀀스의 일부 (추측) |
| 초기 구축 시 선행 | `ACCTFILE`, `CARDFILE` 등 동료 잡 | 데이터 초기 적재 잡 전체가 함께 실행되어야 애플리케이션이 기동된다 |

야간 배치 시퀀스에서의 위치: 이 잡은 **시스템 초기 구축 시 1회성**으로 실행된다. 야간 배치(`POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANIDX → OPENFIL`) 중에는 `DISCGRP.VSAM.KSDS`를 수정하지 않으며, `INTCALC`가 이를 읽기만 한다.

---

## Java/현대화 노트

### 1. VSAM KSDS → 관계형 DB 테이블 매핑

| COBOL/VSAM 개념 | Java/RDBMS 대응 |
|----------------|----------------|
| KSDS (키 순차 데이터셋) | `UNIQUE` 인덱스가 있는 `PRIMARY KEY` 테이블 |
| `KEYS(16 0)` 복합 키 | 복합 PK `(acct_group_id, tran_type_cd, tran_cat_cd)` |
| `RECORDSIZE(50 50)` 고정 길이 | 고정 컬럼 수 테이블; 별도 FILLER 컬럼 불필요 |
| `DIS-INT-RATE PIC S9(4)V99` | `DECIMAL(6,2)` 또는 Java `BigDecimal`; **`double`/`float` 사용 금지** (이자 계산 오차 발생) |

```java
// DIS-INT-RATE PIC S9(04)V99 읽기 예시
// COBOL DISPLAY 저장값: "001250" → 의미: 12.50%
String raw = "001250";  // 6자리 DISPLAY
BigDecimal intRate = new BigDecimal(raw).movePointLeft(2); // → 12.50
```

### 2. IDCAMS REPRO → Flyway/Spring Batch

`IDCAMS REPRO`의 Java 현대화 대응:

```java
// Spring Batch ItemReader/ItemWriter로 초기 데이터 적재
@Bean
public Job discgrpLoadJob(JobBuilderFactory jobs, Step step) {
    return jobs.get("discgrpLoadJob").start(step).build();
}
// FlatFileItemReader → JdbcBatchItemWriter 패턴
// 또는 Flyway V__disclosure_group_data.sql 로 단순 SQL INSERT
```

### 3. SET MAXCC = 0 패턴

메인프레임에서 `DELETE`+`SET MAXCC=0` 조합은 멱등성(idempotency) 보장을 위한 관용구다. Java 현대화 시 이에 대응하는 패턴:

```sql
-- Flyway 또는 Liquibase
DROP TABLE IF EXISTS disclosure_group;
CREATE TABLE disclosure_group (...);
```

또는 Spring Batch의 `JobExecutionDecider`로 파일 존재 여부를 판단 후 생성/스킵.

### 4. SHAREOPTIONS(2 3) 동시성 주의

`SHAREOPTIONS(2 3)`은 여러 잡이 동시에 읽을 수 있고, 단일 리전에서 갱신이 가능함을 의미한다. 메인프레임에서 CICS와 배치가 같은 파일을 공유하는 방식이며, Java에서는 DB 트랜잭션 격리 수준(`READ COMMITTED` 등)으로 대응한다.

### 5. DEFAULT 폴백 패턴

`CBACT04C`의 이자 계산 로직은 특정 그룹 ID로 DISCGRP를 읽고 STATUS `'23'`(레코드 없음)이 반환되면 그룹 ID를 `'DEFAULT'`로 대체하여 재조회한다. Java 현대화 시:

```java
Optional<DisclosureGroup> rate = repo.findById(new DiscGroupKey(groupId, typeCode, catCode));
if (rate.isEmpty()) {
    rate = repo.findById(new DiscGroupKey("DEFAULT", typeCode, catCode));
}
BigDecimal annualRate = rate.orElseThrow().getIntRate(); // scale=2
```

### 6. EBCDIC vs ASCII

원본 데이터 파일 `DISCGRP.PS`는 메인프레임의 EBCDIC 인코딩으로 저장된다. `app/data/ASCII/` 디렉터리에 ASCII 변환본이 있다. AWS M2 환경이나 로컬 개발 시 파일 인코딩 변환(`iconv`, `dd conv=ascii`) 없이 적재하면 `DIS-ACCT-GROUP-ID` 등 문자 필드가 깨진다.
