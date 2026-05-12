document.getElementById('send-btn').addEventListener('click', ask);

document.getElementById('user-input').addEventListener('keypress', (e) => {
    if (e.key === 'Enter') {
        // 입력창이 활성화되어 있을 때만(비활성화 중복 방지) 실행
        if (!document.getElementById('user-input').disabled) {
            ask();
        }
    }
});
async function ask() {
    const inputElement = document.getElementById('user-input');
    const sendButton = document.getElementById('send-btn');
    const chatHistory = document.getElementById('chat-history');
    const question = inputElement.value.trim();


    if (!question)
        return;

    // --- [대기 상태 시작] ---
    // 입력창과 버튼을 비활성화해서 중복 입력을 막음
    inputElement.disabled = true;
    sendButton.disabled = true;
    const originalPlaceholder = inputElement.placeholder;
    inputElement.placeholder = "Gemma가 답변을 생각 중입니다...";

    // 1. 사용자 질문 화면에 추가
    chatHistory.innerHTML += `<div class="user-msg"><strong>나:</strong> ${question}</div>`;
    inputElement.value = '';
    chatHistory.scrollTop = chatHistory.scrollHeight;

    try {
        // 2. 자바 컨트롤러(Back-end)로 데이터 전송
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({"question": question})
        });

        if (!response.ok) { // 200~299 범위가 아닐 경우
            throw new Error(`서버 응답 오류: ${response.status}`);
        }

        const data = await response.json();

        // 3. 서버가 준 답변 화면에 추가
        chatHistory.innerHTML += `<div class="ai-msg"><strong>AI:</strong> ${data.answer}</div>`;

        // 스크롤 하단 이동
        chatHistory.scrollTop = chatHistory.scrollHeight;

    } catch (error) {
        console.error("연결 오류:", error);
        chatHistory.innerHTML += `<p style="color:red;">오류: 서버와 연결할 수 없습니다.</p>`;
    } finally {
        // --- [대기 상태 종료] ---
        // 작업이 끝나면(성공하든 실패하든) 다시 활성화
        inputElement.disabled = false;
        sendButton.disabled = false;
        inputElement.placeholder = originalPlaceholder;
        inputElement.focus(); // 바로 다음 질문 가능하게 포커스

        // 스크롤 하단 이동
        chatHistory.scrollTop = chatHistory.scrollHeight;
    }
}