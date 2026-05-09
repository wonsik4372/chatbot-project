document.getElementById('send-btn').addEventListener('click', ask);

async function ask() {
    const inputElement = document.getElementById('user-input');
    const chatHistory = document.getElementById('chat-history');
    const question = inputElement.value;

    inputElement.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') ask();
    });

    if (!question) return;

    // 1. 사용자 질문 화면에 추가
    chatHistory.innerHTML += `<p><strong>나:</strong> ${question}</p>`;
    inputElement.value = '';

    try {
        // 2. 자바 컨트롤러(Back-end)로 데이터 전송
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ "question": question })
        });

        if (!response.ok) { // 200~299 범위가 아닐 경우
            throw new Error(`서버 응답 오류: ${response.status}`);
        }

        const data = await response.json();

        // 3. 서버가 준 답변 화면에 추가
        chatHistory.innerHTML += `<p><strong>AI:</strong> ${data.answer}</p>`;
        
        // 스크롤 하단 이동
        chatHistory.scrollTop = chatHistory.scrollHeight;

    } catch (error) {
        console.error("연결 오류:", error);
        chatHistory.innerHTML += `<p style="color:red;">오류: 서버와 연결할 수 없습니다.</p>`;
    }
}