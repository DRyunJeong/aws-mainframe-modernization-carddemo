---
name: ims-hierarchical-db
description: IMS HIDAM pending-authorization DB (DBPAUTP0 root PAUTSUM0 / child PAUTDTL1 + index DBPAUTX0), DBD/PSB/PCB structure, and the two access styles (EXEC DLI host commands vs CALL CBLTDLI+SSA)
metadata:
  type: project
---

선택 모듈 app-authorization-ims-db2-mq의 IMS 계층형 DB. DBD/PSB는 app/app-authorization-ims-db2-mq/ims/*.dbd|*.psb (어셈블러 매크로 소스).

== DB 구조 (계층형 = 부모-자식 트리, 관계형 아님) ==
- DBPAUTP0.dbd: 메인 DB. ACCESS=(HIDAM,VSAM). 세그먼트 2개로 된 2단계 트리.
  - 루트 세그먼트 PAUTSUM0(보류 승인 요약, BYTES=100). SEQ 필드 ACCNTID(START=1,BYTES=6,TYPE=P=packed). LCHILD로 인덱스 DBPAUTX0 연결.
  - 자식 세그먼트 PAUTDTL1(보류 승인 상세, BYTES=200, PARENT=PAUTSUM0). SEQ 필드 PAUT9CTS(8바이트, TYPE=C). 1:N — 계정 1건당 상세 N건.
- DBPAUTX0.dbd: HIDAM 1차 인덱스 DB. ACCESS=(INDEX,VSAM,PROT). 세그먼트 PAUTINDX(BYTES=6), INDEX=ACCNTID로 루트의 키를 인덱싱. HIDAM은 "인덱스 DB + 데이터 DB" 2개가 한 쌍 — 인덱스로 루트를 키 직접 접근하고, 데이터 DB 안에서는 포인터로 트리를 항해. (PADFLDBD/PASFLDBD.DBD, DLIGSAMP.PSB 등 다른 파일도 같은 디렉터리에 있으나 미정독 — 필요 시 확인)

계층형 vs 관계형/Java: 루트=부모 엔티티, 자식=소유된 컬렉션. Java로는 `class PendingAuthSummary { String acctId; List<PendingAuthDetail> details; }` (부모가 자식을 소유하는 aggregate). RDB라면 PK/FK 2테이블 + JOIN이지만, IMS는 물리 트리에 자식을 묻어두고 부모 경로로만 도달 — 자식을 독립 조회 불가(부모 먼저 위치시켜야 함). 이것이 "navigational" DB.

== PSB/PCB (프로그램이 보는 뷰 + 권한) ==
PCB = Program Communication Block = 프로그램별 DB 뷰 + 상태 반환 영역. JDBC Connection + 권한+커서 위치를 합친 개념.
- PSBPAUTB.psb: PCB명 PAUTBPCB, PROCOPT=AP(All=조회/갱신), KEYLEN=14. SENSEG로 PAUTSUM0, PAUTDTL1 둘 다 민감(접근 가능)하게 선언. LANG=COBOL. → 온라인/업데이트용.
- PSBPAUTL.psb: PCB명 PAUTLPCB, PROCOPT=L(Load), LANG=ASSEM. → 초기 DB 적재용(로더 PAUDBLOD가 사용).
PROCOPT은 "이 프로그램이 뭘 할 수 있나"를 컴파일 시점에 고정 — Spring의 메서드 권한/readonly 트랜잭션과 유사하나 더 정적.

== 두 가지 접근 스타일 (둘 다 같은 IMS, 추상화 수준만 다름) ==
1) EXEC DLI 호스트 명령 (고수준, 온라인 프로그램). 예: COPAUA0C.cbl 5500-READ-AUTH-SUMMRY (620행 근처):
     EXEC DLI GU USING PCB(PAUT-PCB-NUM) SEGMENT(PAUTSUM0)
        INTO(PENDING-AUTH-SUMMARY) WHERE(ACCNTID = PA-ACCT-ID) END-EXEC
   - GU=Get Unique(키로 단건, =SELECT...WHERE PK), GNP=Get Next within Parent(자식 반복=커서 next), ISRT=Insert, REPL=Replace/update(반드시 직전 GET-HOLD로 읽은 세그먼트만). DELETE는 DLET.
   - 결과 상태는 DIBSTAT를 IMS-RETURN-CODE로 옮겨 EVALUATE. 상태 88레벨(COPAUA0C 86~94행): SPACES/'FW'=정상(STATUS-OK), 'GE'=세그먼트 없음(SEGMENT-NOT-FOUND, =row not found), 'II'=중복, 'GB'=DB끝, 'BA'=DB unavailable. PCB-NUM(PAUT-PCB-NUM=+1)으로 PSB 내 몇 번째 PCB인지 지정.
   COPAUS2C.cbl도 EXEC DLI 다수 사용(다만 일부는 EXEC SQL일 수 있음 — IMS+DB2 혼합 모듈).
2) CALL 'CBLTDLI' + SSA (저수준 콜 인터페이스, 로더 PAUDBLOD.CBL). EXEC DLI는 전처리기가 이 콜로 풀어줌.
   - 함수코드 FUNC-GU/FUNC-ISRT 등(4바이트 상수)을 첫 인자로, PCB 마스크, I/O 영역, SSA를 인자로 CALL.
   - SSA(Segment Search Argument) = WHERE절을 수동 조립한 구조체. ROOT-QUAL-SSA(PAUDBLOD 113행~): 세그먼트명 'PAUTSUM0' + '(' + 키필드 'ACCNTID ' + 연산자 'EQ' + 키값(S9(11)COMP-3) + ')'. UNQUALIFIED SSA(세그먼트명만)는 조건 없이 다음 항해.
   - 상태는 PCB 마스크의 status 바이트(PAUT-PCB-STATUS)를 직접 검사(SPACES=OK, 'II'=dup).
   - PAUDBLOD는 ENTRY 'DLITCBL' USING ...PCB로 IMS가 직접 호출(DLI batch 진입점), 루트 GU→없으면 ISRT, 자식 ISRT 식으로 DB를 초기 적재.

핵심 gotcha: (a) 자식 도달엔 부모 위치가 선행조건(GNP는 "현재 부모 안에서"). (b) REPL/DLET 전 GET-HOLD 필수(낙관적 잠금 아님, 위치 기반). (c) 키 ACCNTID가 packed(COMP-3) — Java Long↔packed 변환 주의. (d) "없음"은 예외 아닌 상태코드('GE')로 옴 — null/Optional.empty 매핑. 관련: [[batch-and-optional-modules]](옵션 모듈 통합 방식), [[copybook-record-layouts]].
