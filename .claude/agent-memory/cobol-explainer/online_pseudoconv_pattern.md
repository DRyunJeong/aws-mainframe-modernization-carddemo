---
name: online-pseudoconv-pattern
description: The standard CICS pseudo-conversational skeleton shared by CO* online programs, and the program-to-program navigation mechanism
metadata:
  type: project
---

CardDemo의 모든 `CO*` 온라인 프로그램은 동일한 pseudo-conversational 골격을 따른다(향후 어떤 CO* 화면을 읽든 이 구조를 가정 가능):

1. WORKING-STORAGE에 WS-PGMNAME, WS-TRANID 리터럴 보유. `COPY COCOM01Y`(commarea), 화면 copybook, `COPY DFHAID`(EIBAID 키 상수: DFHENTER/DFHPF3 등), `COPY DFHBMSCA`(화면 속성).
2. LINKAGE SECTION: `01 DFHCOMMAREA` 를 `PIC X OCCURS 1 TO 32767 DEPENDING ON EIBCALEN`로 받는다.
3. MAIN-PARA 분기: `IF EIBCALEN = 0` (최초 진입, commarea 없음) → 화면 첫 전송 또는 signon으로. ELSE → `MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA` 후, `CDEMO-PGM-REENTER`(88-level) 플래그로 "처음 그리기" vs "사용자 응답 처리"를 구분. 응답 처리 시 `EVALUATE EIBAID`로 ENTER/PF3/기타 키 분기.
4. 매 턴 종료: `EXEC CICS RETURN TRANSID(WS-TRANID) COMMAREA(CARDDEMO-COMMAREA)`. → 다음 키 입력 시 같은 트랜잭션이 재기동되며 commarea로 상태 복원. (HTTP 무상태 요청 + 직렬화 세션 토큰과 동형. RETURN과 RETURN 사이에는 메모리에 아무 것도 남지 않음.)

화면 I/O: `EXEC CICS SEND MAP(...) MAPSET(...) FROM(맵O) ERASE` 로 출력, `EXEC CICS RECEIVE MAP(...) INTO(맵I)` 로 입력. 헤더(제목/날짜/시간/applid/sysid)는 POPULATE-HEADER-INFO에서 채움.

프로그램 간 이동 = `EXEC CICS XCTL PROGRAM('다음프로그램') COMMAREA(CARDDEMO-COMMAREA)` (제어를 넘기고 돌아오지 않음, HTTP 302 redirect + 세션 전달과 유사). 로그인(COSGN00C)은 사용자 타입에 따라 COADM01C(admin) 또는 COMEN01C(user)로 XCTL.

메뉴 디스패치: COMEN01C/COADM01C는 메뉴 옵션 테이블(COMEN02Y/COADM02Y)의 CDEMO-MENU-OPT-PGMNAME(선택번호)를 그대로 XCTL 대상 프로그램명으로 사용 → 데이터 주도 라우팅. COMEN01C는 설치되지 않은 옵션 모듈(예: COPAUS0C)을 `EXEC CICS INQUIRE PROGRAM ... NOHANDLE`로 탐지해 "not installed" 처리.

base 프로그램에서 실제로 쓰는 CICS 동사 빈도(상위): SEND, RETURN, READ, RECEIVE, XCTL, HANDLE, STARTBR/READPREV/READNEXT/ENDBR(브라우즈=커서 페이징), WRITE/REWRITE/DELETE, ASSIGN, ASKTIME/FORMATTIME.
