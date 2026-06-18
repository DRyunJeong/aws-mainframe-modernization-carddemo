# DUSRSECJ — 사용자 보안 파일 초기 구축 잡

- **유형**: JCL (배치 초기화 잡)
- **한 줄 요약**: 인라인 데이터로 평문 USRSEC 순차파일(PS)을 생성한 뒤 VSAM KSDS 클러스터를 정의하고 데이터를 로드하여, CardDemo 온라인 프로그램이 사용할 사용자 인증 마스터 파일을 처음부터 구축한다.

---

## 기능 설명

DUSRSECJ는 CardDemo 설치 초기 또는 사용자 보안 파일을 완전히 재구축해야 할 때 1회성으로 실행하는 **시드(seed) 데이터 로드 잡**이다. 실행 흐름은 세 단계로 구성된다.

1. **사전 정리(PREDEL)**: 이전에 생성된 순차파일(PS)이 남아 있을 경우 삭제한다. IEFBR14는 아무 처리도 하지 않는 더미 프로그램으로, DD 문의 `DISP=(MOD,DELETE,DELETE)` 속성만으로 파일을 삭제하는 관용적 메인프레임 패턴이다. Java로 치면 `Files.deleteIfExists()` 한 줄에 해당한다.

2. **PS 순차파일 생성(STEP01)**: IEBGENER가 JCL 내 인라인 데이터(`DD *`)를 읽어 80바이트 고정길이(FB) 순차파일로 복사한다. 이 단계가 사용자 초기 데이터 삽입에 해당하며, 5명의 관리자(ADMIN001~ADMIN005)와 5명의 일반 사용자(USER0001~USER0005) 총 10건의 레코드가 포함된다.

3. **VSAM KSDS 정의 및 데이터 로드(STEP02/STEP03)**: IDCAMS가 먼저 기존 VSAM 클러스터를 `DELETE`하고(`SET MAXCC=0`으로 클러스터 미존재 시 오류를 무시) 새 클러스터를 `DEFINE`한다. 이어 STEP03에서 같은 IDCAMS를 통해 `REPRO` 명령으로 PS 파일의 10건 레코드를 VSAM KSDS에 복사하면 파일 구축이 완료된다.

### 인라인 레코드 레이아웃

JCL 내 인라인 데이터의 레코드 구조는 copybook `CSUSR01Y`의 `SEC-USER-DATA`(80바이트)와 일치한다.

| 오프셋(1-base) | 길이 | 필드명 | 예시값 | 설명 |
|---|---|---|---|---|
| 1–8 | 8 | `SEC-USR-ID` | `ADMIN001` | 사용자 ID (VSAM 키, 오프셋 0부터 8바이트) |
| 9–28 | 20 | `SEC-USR-FNAME` | `MARGARET` | 이름 (공백 패딩) |
| 29–48 | 20 | `SEC-USR-LNAME` | `GOLD` | 성 (공백 패딩) |
| 49–56 | 8 | `SEC-USR-PWD` | `PASSWORDA` | 비밀번호 (**평문 저장**) |
| 57 | 1 | `SEC-USR-TYPE` | `A` / `U` | 사용자 유형 (`A`=관리자, `U`=일반) |
| 58–80 | 23 | `SEC-USR-FILLER` | (공백) | 미사용 패딩 |

> **주의**: `PASSWORDA`는 8바이트인데 `SEC-USR-PWD` 필드 역시 8바이트(`PIC X(08)`)이므로 정확히 맞는다. 단, 비밀번호가 **평문(cleartext)**으로 저장된다는 점은 현대화 시 반드시 개선해야 할 보안 취약점이다.

---

## 스텝 구성

| 스텝명 | EXEC PGM | 역할 |
|---|---|---|
| `PREDEL` | `IEFBR14` | 기존 순차파일 `AWS.M2.CARDDEMO.USRSEC.PS` 사전 삭제 (MOD,DELETE,DELETE) |
| `STEP01` | `IEBGENER` | 인라인 데이터 10건을 LRECL=80, RECFM=FB PS 파일로 작성 |
| `STEP02` | `IDCAMS` | 기존 VSAM 클러스터 삭제 후 신규 KSDS 클러스터 정의 (KEYS(8,0), RECORDSIZE(80,80)) |
| `STEP03` | `IDCAMS` | REPRO 명령으로 PS → VSAM KSDS 데이터 복사 |

---

## 의존성

- **COPY (PROC/INCLUDE)**: 없음 (독립 실행 JCL, 카탈로그 프로시저 미사용)

- **호출 프로그램 (EXEC PGM)**:
  - `IEFBR14` — z/OS 내장 더미 프로그램. 실행 시 아무 처리를 하지 않고 RC=0으로 종료하며, DD 처리(DISP) 부수효과만을 이용한다.
  - `IEBGENER` — z/OS 내장 순차파일 복사 유틸리티. SYSUT1(입력) → SYSUT2(출력) 복사. SYSIN=DUMMY이면 레코드 변환 없이 단순 복사한다.
  - `IDCAMS` — z/OS VSAM 관리 유틸리티. `DELETE`, `DEFINE CLUSTER`, `REPRO` 등 VSAM 파일 생명주기 전체를 담당한다.

- **데이터셋/파일/DB 테이블**:
  - `AWS.M2.CARDDEMO.USRSEC.PS` — 중간 경유 순차파일 (LRECL=80, RECFM=FB, DSORG=PS). STEP01에서 생성, STEP03에서 REPRO 원본으로 사용. STEP03 완료 후에는 삭제해도 무방하나 JCL상 명시적 삭제 없음.
  - `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS` — 최종 목적 VSAM KSDS 클러스터. 키 오프셋 0, 키 길이 8(`SEC-USR-ID`). 고정 레코드 80바이트, 45트랙 초기 + 15트랙 보조 할당, CI 크기 8192바이트.
  - `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS.DAT` — KSDS 데이터 컴포넌트 (IDCAMS DEFINE 시 명시)
  - `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS.IDX` — KSDS 인덱스 컴포넌트 (IDCAMS DEFINE 시 명시)

- **선행/후행 잡**:
  - 선행: 없음. 설치 초기화 단계에서 독립적으로 실행 가능. 단, VSAM 클러스터가 CICS 리전에 열려 있는 상태라면 반드시 CICS를 내린 후 실행해야 한다.
  - 후행: CICS 리전 기동 또는 재기동. `remote_refresh.sh`의 `DUSRSECJ` 단계와 동일한 역할. 이후 `COSGN00C`(로그인 화면) 프로그램이 `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS`를 READ로 접근하여 인증을 처리한다.

---

## Java/현대화 노트

### 1. IEFBR14 + MOD,DELETE,DELETE 패턴 → `Files.deleteIfExists()`

```cobol
//PREDEL  EXEC PGM=IEFBR14
//DD01     DD DSN=AWS.M2.CARDDEMO.USRSEC.PS,
//            DISP=(MOD,DELETE,DELETE)
```

JCL에서 파일 삭제는 `DISP=(MOD,DELETE,DELETE)` 속성을 가진 DD 문을 IEFBR14와 조합해 표현한다. `MOD`는 파일이 없어도 오류 없이 진행하게 하는 관용구이고, 첫 번째 `DELETE`는 정상 종료 시, 두 번째 `DELETE`는 비정상 종료 시 처리를 의미한다. Java 등가 코드:

```java
// Java 등가
Path ps = Path.of("/data/USRSEC.PS");
Files.deleteIfExists(ps);
```

### 2. IEBGENER 인라인 데이터 → 시드 데이터 삽입 (SQL INSERT / Flyway migration)

```cobol
//SYSUT1   DD *
ADMIN001MARGARET            GOLD                PASSWORDA
...
/*
```

`DD *`는 JCL 스트림 내부에 데이터를 직접 포함시키는 인라인 데이터 선언이다(`/*`로 종료). 이는 Java 마이그레이션에서 Flyway/Liquibase의 초기 시드 SQL 스크립트(`V1__insert_users.sql`)에 해당한다.

```sql
-- Java/현대화 등가 (예시)
INSERT INTO users (user_id, first_name, last_name, password_hash, user_type)
VALUES ('ADMIN001', 'MARGARET', 'GOLD', hash('PASSWORDA'), 'A');
```

> **보안 경고**: 현재 `SEC-USR-PWD`(`PIC X(08)`)에 비밀번호가 **평문으로 저장**된다(라인 35–44, `PASSWORDA`/`PASSWORDU`). 현대화 시 반드시 BCrypt/Argon2 등 단방향 해시로 교체하고, 시드 스크립트에도 해시값만 기록해야 한다(PCI-DSS 및 일반 보안 요건).

### 3. IDCAMS DEFINE CLUSTER → VSAM KSDS ≒ JPA Entity + 기본키 인덱스

```
DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS)
                KEYS(8,0)
                RECORDSIZE(80,80)
                ...)
```

`KEYS(8,0)`은 레코드의 오프셋 0(1-base 아님, 0-base)부터 8바이트가 KSDS의 기본 키임을 선언한다. 이는 `SEC-USR-ID` 필드와 일치한다. `RECORDSIZE(80,80)`은 최소/최대가 동일한 고정길이 레코드임을 의미한다. Java/JPA 등가:

```java
@Entity
@Table(name = "users")
public class SecUserData {
    @Id
    @Column(name = "sec_usr_id", length = 8)
    private String secUsrId;           // PIC X(08), KEYS(8,0)

    @Column(name = "sec_usr_fname", length = 20)
    private String secUsrFname;        // PIC X(20)

    @Column(name = "sec_usr_lname", length = 20)
    private String secUsrLname;        // PIC X(20)

    @Column(name = "sec_usr_pwd", length = 8)
    private String secUsrPwd;          // PIC X(08) — 현대화 시 해시로 교체

    @Column(name = "sec_usr_type", length = 1)
    private String secUsrType;         // PIC X(01): 'A'=admin, 'U'=user
    // sec_usr_filler PIC X(23) — 무시 또는 미매핑
}
```

### 4. SET MAXCC = 0 → 예외 무시 패턴

```
DELETE  AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS
SET     MAXCC = 0
```

IDCAMS의 `SET MAXCC = 0`은 앞선 `DELETE` 명령이 대상 클러스터 미존재로 실패(RC=8)해도 후속 `DEFINE` 단계가 중단되지 않도록 최대 컨디션 코드를 강제로 0으로 낮추는 관용구다. Java 등가:

```java
try {
    dropTableIfExists("USRSEC_VSAM_KSDS");
} catch (DataAccessException ignored) {
    // 존재하지 않는 경우 무시, SET MAXCC=0 에 해당
}
createTable("USRSEC_VSAM_KSDS");
```

### 5. IDCAMS REPRO → 배치 INSERT / ETL 로드

STEP03의 `REPRO INFILE(IN) OUTFILE(OUT)`은 PS 순차파일의 레코드를 VSAM KSDS에 레코드 단위로 삽입하는 벌크 로드 명령이다. Java로 치면 `JdbcTemplate.batchUpdate()` 또는 Spring Batch의 `ItemWriter`를 통한 벌크 INSERT에 해당한다.

### 6. FREESPACE(10,15) — VSAM 여유 공간

`FREESPACE(10,15)`는 CI(Control Interval)당 10%, CA(Control Area)당 15%를 여유 공간으로 예약하여 추후 레코드 삽입 시 CI/CA 분할(split)을 최소화한다. RDB 인덱스의 `FILLFACTOR`와 동일한 개념이다. 시드 데이터가 10건뿐이어서 실질적 영향은 없으나, 운영 중 사용자가 추가될 경우 성능 튜닝 파라미터로 작용한다.

---

*소스 파일*: `app/jcl/DUSRSECJ.jcl`
*레코드 레이아웃 copybook*: `app/cpy/CSUSR01Y.cpy` (`SEC-USER-DATA`, 80바이트)
*버전*: CardDemo_v1.0-15-g27d6c6f-68 (2022-07-19)
