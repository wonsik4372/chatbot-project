const chatForm = document.getElementById('chat-form');
const userInput = document.getElementById('user-input');
const chatWindow = document.getElementById('chat-window');
const sendBtn = document.getElementById('send-btn'); // 버튼 제어를 위해 추가

chatForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const question = userInput.value.trim();
    if (!question) return;

    // --- [중요] 질문 시작 시 입력창과 버튼 비활성화 ---
    setDisabled(true);

    // 1. 사용자 메시지 화면에 추가
    addMessage(question, 'user');
    userInput.value = '';

    // 2. 봇 "생각 중..." 표시
    const loadingMessage = addMessage("대답을 생각 중이에요...", 'bot');

    try {
        // 3. 백엔드 API 호출
        const response = await fetch('http://localhost:8080/api/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question: question })
        });

        const data = await response.json();
        
        // 4. 로딩 메시지 제거 후 실제 답변 타이핑 효과 시작
        chatWindow.removeChild(loadingMessage);
        
        // 답변이 끝난 후 비활성화를 해제하도록 콜백 전달
        typeWriter(data.answer, 'bot', () => {
            setDisabled(false); // 타이핑 종료 후 다시 활성화
        });

    } catch (error) {
        chatWindow.removeChild(loadingMessage);
        addMessage("서버와 연결할 수 없습니다. 다시 시도해 주세요.", 'bot');
        setDisabled(false); // 에러 발생 시에도 다시 질문할 수 있게 해제
    }
});

// 입력창과 버튼의 상태를 조절하는 함수
function setDisabled(state) {
    userInput.disabled = state;
    sendBtn.disabled = state;
    
    // 비활성화 시 시각적인 피드백 (반투명 처리)
    if (state) {
        chatForm.style.opacity = "0.6";
        userInput.placeholder = "답변을 기다리는 중...";
    } else {
        chatForm.style.opacity = "1";
        userInput.placeholder = "질문을 입력하세요...";
        userInput.focus(); // 다시 활성화되면 자동으로 포커스
    }
}

function addMessage(text, sender) {
    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message', sender);
    messageDiv.innerHTML = `<div class="bubble">${text}</div>`;
    chatWindow.appendChild(messageDiv);
    chatWindow.scrollTop = chatWindow.scrollHeight;
    return messageDiv;
}

// 수정된 타이핑 함수 (콜백 기능 추가)
function typeWriter(text, sender, callback) {
    const messageDiv = document.createElement('div');
    messageDiv.classList.add('message', sender);
    const bubble = document.createElement('div');
    bubble.classList.add('bubble');
    messageDiv.appendChild(bubble);
    chatWindow.appendChild(messageDiv);

    let i = 0;
    const interval = setInterval(() => {
        bubble.innerText += text.charAt(i);
        i++;
        chatWindow.scrollTop = chatWindow.scrollHeight;
        if (i >= text.length) {
            clearInterval(interval);
            if (callback) callback(); // 모든 글자가 써지면 비활성화 해제 실행
        }
    }, 20);
}