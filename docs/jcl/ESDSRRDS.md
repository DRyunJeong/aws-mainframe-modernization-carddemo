# ESDSRRDS — 사용자 보안 파일 초기 생성 (ESDS·RRDS)

- **유형**: JCL (VSAM 클러스터 정의 및 초기 데이터 적재)
- **한 줄 요약**: 인스트림 데이터(관리자 5명+일반사용자 5명)를 원본으로 사용자 보안 VSAM 파일을 ESDS와 RRDS 두 조직으로 각각 정의·적재하는 초기화 잡.

---

## 기능 설명

CardDemo 시스템의 **사용자 보안 마스터 파일**을 메인프레임에 처음 구성할 때 실행하는 설치용 JCL이다.
한 번의 실행으로 다섯 단계에 걸쳐 다음 세 가지를 완료한다.

1. **중간 순차 파일(PS)** 작성 — JCL 내부 인스트림 데이터(\*/\* 구분)를 IEBGENER로 고정 80바이트 순차 파일에 복사한다.
2. **VSAM ESDS(Entry-Sequenced Data Set) 정의 및 적재** — IDCAMS로 클러스터를 생성하고 REPRO로 PS 데이터를 복사한다.
3. **VSAM RRDS(Relative Record Data Set) 정의 및 적재** — 동일 PS 데이터를 상대 레코드 조직으로도 별도 클러스터를 정의하여 복사한다.

ESDS는 **삽입 순서대로 접근**하는 조직이고, RRDS는 **상대 레코드 번호(RRN)로 직접 접근**하는 조직이다. 두 파일 모두 동일한 인스트림 레코드를 담지만, 온라인·배치 프로그램이 각자 선호하는 접근 방식에 맞는 파일을 선택할 수 있도록 병렬 생성한다.

실제 런타임에 CICS 프로그램(`COSGN00C` 등)은 `CSUSR01Y` copybook으로 정의된 `SEC-USER-DATA` 레코드(SEC-USR-ID / SEC-USR-PWD / SEC-USR-TYPE)를 이 파일에서 읽어 인증한다.

---

## 스텝 구성

| 스텝명 | EXEC PGM | 역할 |
|--------|----------|------|
| PREDEL | IEFBR14 | 기존 중간 PS 파일(`AWS.M2.CARDDEMO.ESDSRRDS.PS`) 사전 삭제. IEFBR14 자체는 아무것도 실행하지 않으며, DD 카드의 `DISP=(MOD,DELETE,DELETE)`가 파일을 삭제한다. 잡을 재실행해도 오류가 발생하지 않도록 하는 idempotent 패턴. |
| STEP01 | IEBGENER | 인스트림 데이터(SYSUT1=`*`)를 읽어 순차 파일 `AWS.M2.CARDDEMO.ESDSRRDS.PS`(LRECL=80, RECFM=FB)를 새로 생성(DISP=NEW,CATLG). SYSIN=DUMMY이므로 제어 카드 없이 단순 복사. 레코드 10건(관리자 5 + 일반 5). |
| STEP02 | IDCAMS | VSAM ESDS 클러스터 `AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS` 정의. 먼저 DELETE(실패해도 `SET MAXCC=0`으로 0으로 리셋하여 계속 진행), 이후 DEFINE CLUSTER(`NONINDEXED`=ESDS 조직). |
| STEP03 | IDCAMS | `REPRO INFILE(IN) OUTFILE(OUT)`으로 PS → ESDS 데이터 복사. |
| STEP04 | IDCAMS | VSAM RRDS 클러스터 `AWS.M2.CARDDEMO.USRSEC.VSAM.RRDS` 정의. ESDS와 동일 파라미터이나 `NUMBERED`(RRDS 조직 지정) 키워드가 `NONINDEXED` 대신 사용된다. |
| STEP05 | IDCAMS | `REPRO INFILE(IN) OUTFILE(OUT)`으로 PS → RRDS 데이터 복사. |

---

## 스텝별 주요 파라미터 상세

### PREDEL — IEFBR14 (라인 24–27)

```jcl
//PREDEL  EXEC PGM=IEFBR14
//DD01     DD DSN=AWS.M2.CARDDEMO.ESDSRRDS.PS,
//            DISP=(MOD,DELETE,DELETE)
```

- `IEFBR14`는 "아무것도 하지 않는" IBM 유틸리티 프로그램이다. DD 카드에 부여된 `DISP` 처리가 목적이다.
- `DISP=(MOD,DELETE,DELETE)`: 정상 종료(DELETE) 또는 이상 종료(DELETE) 양쪽 모두 파일을 삭제·언카탈로그한다. 파일이 없어도 `MOD`이므로 abend 없이 통과한다.

### STEP01 — IEBGENER 인스트림 데이터 (라인 33–53)

```jcl
//SYSUT1   DD *
ADMIN001MARGARET            GOLD                PASSWORDA
...
USER0005LEE                 TING                PASSWORDU
/*
//SYSUT2   DD DSN=AWS.M2.CARDDEMO.ESDSRRDS.PS,
//            DISP=(NEW,CATLG,DELETE),
//            DCB=(LRECL=80,RECFM=FB,DSORG=PS,BLKSIZE=0),
//            UNIT=SYSAD,SPACE=(TRK,(10,5),RLSE)
```

각 레코드(80바이트 고정)의 레이아웃은 `app/cpy/CSUSR01Y`의 `SEC-USER-DATA`와 일치한다:

| 오프셋 | 길이 | 내용 | 예시 |
|--------|------|------|------|
| 1–7 | 7 | SEC-USR-ID | `ADMIN001` |
| 8–27 | 20 | SEC-USR-FNAME | `MARGARET` |
| 28–47 | 20 | SEC-USR-LNAME | `GOLD` |
| 48–57 | 10 | SEC-USR-PWD | `PASSWORDA` |
| 마지막 1바이트 | 1 | SEC-USR-TYPE | `A`(admin) / `U`(user) |

> **주의**: 파일 마지막 문자(`A`/`U`)가 SEC-USR-TYPE이다 — 80번째 컬럼. 비밀번호가 평문(plaintext)으로 저장된다.

### STEP02 — IDCAMS ESDS DEFINE CLUSTER (라인 59–73)

```
DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS)
                RECORDSIZE(80,80)
                REUSE
                NONINDEXED          ← ESDS 조직
                TRACKS(45,15)
                FREESPACE(10,15)
                CISZ(8192))
       DATA    (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS.DAT))
```

- `NONINDEXED`: ESDS(Entry-Sequenced) 조직. 키 없이 물리적 삽입 순서대로 레코드를 보관한다.
- `REUSE`: 클러스터를 다시 열 때 기존 데이터를 비우고 처음부터 다시 쓸 수 있게 한다(재적재 편의).
- `CISZ(8192)`: Control Interval 크기 8 KB. I/O 단위.
- `FREESPACE(10,15)`: CI의 10%, CA의 15%를 여유 공간으로 남긴다(향후 레코드 추가를 위한 공간).
- `TRACKS(45,15)`: 초기 45트랙 할당, 부족 시 15트랙씩 확장.
- DATA 컴포넌트는 별도 이름(`...ESDS.DAT`)으로 관리된다. ESDS는 인덱스 컴포넌트가 없으므로 DATA만 존재.

### STEP04 — IDCAMS RRDS DEFINE CLUSTER (라인 93–107)

```
DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.RRDS)
                RECORDSIZE(80,80)
                REUSE
                NUMBERED            ← RRDS 조직
                TRACKS(45,15)
                FREESPACE(10,15)
                CISZ(8192))
       DATA    (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.RRDS.DAT))
```

- `NUMBERED`: RRDS(Relative Record Data Set) 조직. 각 레코드가 고정 길이 슬롯에 저장되며 상대 레코드 번호(1-based integer RRN)로 직접 접근 가능하다.
- RRDS는 ESDS와 달리 `READ ... KEY IS numeric-rrn`으로 특정 슬롯을 바로 읽을 수 있다.
- RRDS에도 인덱스 컴포넌트가 없으므로 DATA 컴포넌트만 정의.

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 — 모든 스텝이 인라인 유틸리티 PGM 직접 호출이며 별도 PROC를 INCLUDE하지 않는다.

- **호출 프로그램 (EXEC PGM)**:
  - `IEFBR14` — IBM 표준 더미 유틸리티 (파일 삭제용)
  - `IEBGENER` — IBM 순차 파일 복사/생성 유틸리티
  - `IDCAMS` — IBM 범용 VSAM 관리 유틸리티 (클러스터 정의·삭제·데이터 복사)

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.ESDSRRDS.PS` — 중간 순차 파일(LRECL=80, RECFM=FB). PREDEL에서 사전 삭제, STEP01에서 생성, STEP03/05에서 입력으로 사용.
  - `AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS` — 사용자 보안 VSAM ESDS 클러스터(출력). 대응 DATA 컴포넌트: `...ESDS.DAT`.
  - `AWS.M2.CARDDEMO.USRSEC.VSAM.RRDS` — 사용자 보안 VSAM RRDS 클러스터(출력). 대응 DATA 컴포넌트: `...RRDS.DAT`.
  - 레코드 레이아웃 copybook: `app/cpy/CSUSR01Y` (`SEC-USER-DATA`)

- **선행/후행 잡**:
  - 선행: 없음 — 이 잡은 시스템 초기 설치 시 단독 실행. 야간 배치 시퀀스(CLOSEFIL→POSTTRAN→INTCALC→…→OPENFIL)와 무관하다.
  - 후행: OPENFIL 잡 (CICS가 오픈한 상태에서 ESDS/RRDS 파일이 사용되려면 CICS region 재시작 또는 CICS OPEN FILE 명령 필요). 런타임 참조 프로그램: `COSGN00C`(사인온 인증), `COUSR00C`/`COUSR01C`/`COUSR02C`(사용자 관리).

---

## Java/현대화 노트

### 1. ESDS vs RRDS — Java 매핑

| VSAM 조직 | 접근 방식 | Java 현대화 유사체 |
|-----------|-----------|-------------------|
| ESDS (NONINDEXED) | 물리 삽입 순서 순차 접근, RBA(Relative Byte Address)로만 직접 접근 | `ArrayList<UserRecord>` 또는 append-only log/파일 스트리밍 |
| RRDS (NUMBERED) | 상대 레코드 번호(1-based RRN)로 직접 접근 | `HashMap<Integer, UserRecord>` 또는 배열 인덱스 접근 |
| KSDS (이 잡에는 없음) | 주 키로 직접 접근 | `TreeMap<String, UserRecord>` 또는 RDBMS B-Tree 인덱스 |

실제 CardDemo 온라인 프로그램(`COSGN00C`)은 사용자 ID 키를 가진 **KSDS**(`USRSECF`)를 사용하는 경우가 많다. 이 잡은 ESDS/RRDS 버전을 추가로 생성하여 다양한 접근 패턴을 시연하는 데모 목적이 크다.

### 2. IEFBR14 + DISP DELETE 패턴 → idempotent 초기화

```cobol
// PREDEL  EXEC PGM=IEFBR14
//DD01      DD DSN=...,DISP=(MOD,DELETE,DELETE)
```

Java/Spring Boot 초기화 코드 동치:

```java
// 있으면 지우고, 없으면 그냥 넘어가는 idempotent 패턴
Files.deleteIfExists(Path.of("/data/usrsec.ps"));
// 또는 JPA/Flyway의 IF EXISTS DROP TABLE
```

### 3. IEBGENER 인스트림 데이터 → 시드 데이터

JCL `DD *` 인스트림 데이터는 현대 시스템에서 **데이터베이스 마이그레이션 시드 파일**(`db/seed.sql`, Flyway `V1__seed_users.sql`)에 해당한다.

```sql
-- 대응 SQL 시드
INSERT INTO user_security (user_id, first_name, last_name, password, user_type)
VALUES ('ADMIN001', 'MARGARET', 'GOLD', 'PASSWORDA', 'A'),
       ('USER0001', 'LAWRENCE', 'THOMAS', 'PASSWORDU', 'U');
```

### 4. 평문 비밀번호 — 보안 위험 (라인 36–45)

인스트림 데이터의 비밀번호(`PASSWORDA`, `PASSWORDU`)가 JCL 소스에 평문으로 노출된다. 현대화 시 반드시:

- 비밀번호를 BCrypt/Argon2 해시로 저장
- 소스 코드/JCL에서 자격 증명 제거 (AWS Secrets Manager, HashiCorp Vault 등으로 외부화)
- PCI-DSS 또는 사내 보안 정책 준수 검토

### 5. IDCAMS DEFINE CLUSTER → VSAM 생성 → 현대 스토리지

```
DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS)
                RECORDSIZE(80,80) REUSE NONINDEXED ...)
```

현대화 목표에 따라 두 가지 전략:

**A. 파일 기반 유지 (리호스팅):** AWS Mainframe Modernization(M2) 또는 Micro Focus Enterprise Server에서 VSAM 에뮬레이션 계층이 IDCAMS 명령을 그대로 해석한다.

**B. RDBMS 전환 (리팩터링):**

```java
// CSUSR01Y SEC-USER-DATA → JPA Entity
@Entity
@Table(name = "user_security")
public class UserSecurityRecord {
    @Id
    @Column(name = "user_id", length = 7)
    private String userId;                     // SEC-USR-ID PIC X(7)

    @Column(name = "first_name", length = 20)
    private String firstName;                  // SEC-USR-FNAME PIC X(20)

    @Column(name = "last_name", length = 20)
    private String lastName;                   // SEC-USR-LNAME PIC X(20)

    @Column(name = "password", length = 10)    // ← 현대화 시 해시로 교체 필수
    private String password;                   // SEC-USR-PWD PIC X(10)

    @Column(name = "user_type", length = 1)
    private String userType;                   // SEC-USR-TYPE PIC X(1) 'A'/'U'
}
```

### 6. SET MAXCC = 0 패턴 — 오류 억제

```
DELETE AWS.M2.CARDDEMO.USRSEC.VSAM.ESDS
SET MAXCC = 0
```

IDCAMS에서 DELETE가 파일 미존재로 실패(RC=8)하면 잡이 후속 스텝을 건너뛸 수 있다. `SET MAXCC=0`으로 리턴 코드를 강제로 0으로 리셋하여 계속 진행하게 한다. Java/Shell 유사 패턴:

```bash
# Shell 동치
rm -f /vsam/usrsec.esds || true   # 실패 무시
```

```java
// Java 동치
try { Files.delete(path); } catch (NoSuchFileException ignored) {}
```

### 7. FREESPACE / CISZ 튜닝 파라미터

- `CISZ(8192)`: VSAM의 I/O 단위(Control Interval). RDBMS의 **페이지 크기**(InnoDB `innodb_page_size`, PostgreSQL `block_size`) 개념에 대응한다.
- `FREESPACE(10,15)`: 삽입 성능을 위해 사전 예약하는 여유 공간. RDBMS `FILLFACTOR`(PostgreSQL) 또는 InnoDB `innodb_fill_factor`에 대응한다.
- 이 잡은 ESDS이므로 레코드는 항상 파일 끝에 추가된다 — FREESPACE는 RRDS의 빈 슬롯 예약 측면이 더 강하다.

### 8. RRDS RRN 1-based 인덱스 주의

RRDS의 상대 레코드 번호는 **1부터 시작**한다(Java 배열과 달리 0-based가 아님). REPRO로 10건 적재 시 RRN 1–10에 배치된다. Java로 접근할 때:

```java
int rrn = 1;  // COBOL RRDS RRN은 1-based
// Java 배열로 변환 시: array[rrn - 1]
```

---

*소스 기준 버전: CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19)*
