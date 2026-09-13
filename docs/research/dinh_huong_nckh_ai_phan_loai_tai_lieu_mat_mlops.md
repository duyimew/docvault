# Định hướng nâng cấp DocVault thành NCKH/Khóa luận: AI phân loại tài liệu mật + MLOps chống drift

Ngày soạn: 2026-06-25

Tài liệu này hệ thống hóa các góp ý của GVHD về việc nâng dự án DocVault hiện tại (hệ thống quản lý tài liệu bảo mật cloud-native, DevSecOps-first) lên hướng nghiên cứu khoa học / khóa luận tốt nghiệp. Trọng tâm là bổ sung một năng lực AI cốt lõi: **tự động đánh giá một tài liệu có thực sự thuộc diện cần bảo mật hay không**, kèm theo một pipeline **MLOps** đảm bảo mô hình không bị suy giảm chất lượng theo thời gian (drift).

Nội dung được viết để vừa là định hướng kỹ thuật, vừa làm cơ sở học thuật (đặt vấn đề, phương pháp, kiến trúc, đánh giá) cho phần mở rộng của báo cáo.

---

## 1. Tóm tắt góp ý của thầy

Ghi chú gốc từ buổi trao đổi:

1. Dùng ML / DL / Reinforcement Learning để **kiểm tra tài liệu có thực sự là tài liệu mật hay không**, vì người dùng nhiều khi không tự xác định đúng mức độ nhạy cảm.
2. Có thể dùng **Deep Learning train trước**, rồi **chuyển (pass) sang Reinforcement Learning** để tăng tốc khả năng học của mô hình.
3. Nếu đi theo hướng đó thì bước sang địa hạt **MLOps**, và khi đó sẽ phát sinh **vấn đề drift**.
4. Sau một thời gian, **AI sẽ bị drift về dữ liệu** (phân phối dữ liệu thực tế lệch khỏi dữ liệu huấn luyện).
5. Vì vậy phải **xây dựng hệ thống dùng Kubeflow hoặc MLflow** để tạo **pipeline cập nhật dữ liệu, chống drift**.

Diễn giải lại thành một câu định vị nghiên cứu:

> Xây dựng và đánh giá một hệ thống phân loại mức độ nhạy cảm tài liệu (sensitivity/classification) dựa trên Deep Learning, được tinh chỉnh bằng Reinforcement Learning từ phản hồi của con người (compliance officer/admin), vận hành dưới một pipeline MLOps có khả năng giám sát và chống suy giảm mô hình do data/concept drift.

---

## 2. Vì sao bài toán này có giá trị nghiên cứu

DocVault hiện cho phép người dùng **tự gán** classification `PUBLIC / INTERNAL / CONFIDENTIAL / SECRET`. Đây là điểm yếu cố hữu của mọi DMS dựa trên phân loại thủ công:

- Người tạo tài liệu **không phải lúc nào cũng biết** đâu là thông tin nhạy cảm (PII, bí mật kinh doanh, thông tin hợp đồng, dữ liệu tài chính...).
- Phân loại sai gây hai loại rủi ro đối lập: **gán quá thấp** dẫn tới rò rỉ dữ liệu mật; **gán quá cao** làm tắc nghẽn quy trình, tăng chi phí kiểm soát.
- Khối lượng tài liệu lớn khiến rà soát thủ công không khả thi.

Đây chính là khoảng trống mà mục **W-P3 (AI auto-classification)** trong kế hoạch cải thiện đã đánh dấu là "future work". Góp ý của thầy biến khoảng trống này thành **đóng góp khoa học chính** của khóa luận, thay vì chỉ là một tính năng phụ.

Tính mới và tính nghiên cứu thể hiện ở ba điểm:

1. **Kết hợp DL + RLHF cho bài toán phân loại nhạy cảm** (không chỉ là fine-tune classifier thông thường) — học từ phản hồi của chuyên gia tuân thủ.
2. **Vòng đời MLOps khép kín**: phát hiện drift → kích hoạt retraining → đánh giá → triển khai an toàn, gắn trực tiếp vào audit/governance sẵn có của DocVault.
3. **Ràng buộc bảo mật/tuân thủ**: mô hình AI phải tôn trọng đúng chính sách RBAC/ACL hiện tại (AI không được "đọc" tài liệu mà chính người dùng/AI không có quyền đọc).

---

## 3. Cơ sở lý thuyết và công trình liên quan

### 3.1. Phân loại tài liệu nhạy cảm bằng DL/NLP

- Các mô hình **Transformer tiền huấn luyện** (BERT/RoBERT-family) đã được dùng để **phát hiện câu/tài liệu chứa thông tin nhạy cảm**, kể cả khi sự nhạy cảm mang tính ngữ cảnh chứ không chỉ từ khóa ([Timmer et al., Monsanto case study](https://arxiv.org/abs/2203.06793)).
- Hướng **phân loại độ nhạy cảm / quản lý bảo mật tài liệu thời gian thực** trên khối lượng văn bản lớn ([MDPI Applied Sciences 2024](https://www.mdpi.com/2076-3417/14/4/1565/xml)).
- Hướng **LLM-based document sensitivity classification theo policy do người dùng định nghĩa** (đã xuất hiện trong sáng chế, [US Patent Application 20260134024](https://patents.justia.com/patent/20260134024)) — cho thấy đây là vấn đề công nghiệp đang được giải quyết, củng cố tính thời sự.
- Bài toán **NER + nhận dạng PII** để gắn nhãn nhạy cảm phục vụ tuân thủ GDPR ([NLP engine GDPR](https://mindcraft.ai/building-a-gdpr-compliant-nlp-engine/)).

Hệ quả thiết kế: phần DL nên là một **encoder Transformer** (ví dụ multilingual BERT/XLM-R cho tài liệu tiếng Việt + tiếng Anh) làm backbone phân loại 4 lớp classification, có thể bổ sung **lớp phát hiện PII** làm đặc trưng phụ trợ.

### 3.2. Deep Learning "train trước" rồi chuyển sang Reinforcement Learning

Đúng theo ý thầy, đây là mô thức **pretrain → RL fine-tune**:

- **Giai đoạn 1 (Supervised pretraining):** huấn luyện classifier có giám sát trên dữ liệu đã gán nhãn. Đây là "khởi động nguội" (warm start) tạo policy ban đầu.
- **Giai đoạn 2 (RL fine-tuning):** dùng **Reinforcement Learning from Human Feedback (RLHF)** để tinh chỉnh theo phản hồi chuyên gia. RLHF gồm: thu thập **preference data** (chuyên gia xác nhận/sửa nhãn) → huấn luyện **reward model** → tối ưu policy bằng RL ([GeeksforGeeks RLHF](https://www.geeksforgeeks.org/machine-learning/reinforcement-learning-from-human-feedback/); [RLHF Book, Lambert 2026](https://arxiv.org/html/2504.12501v5)).
- Có khung **RLHF chuyên cho bài toán phân loại văn bản** (ClaHF — tích hợp preference modeling + RL vào classification, [arXiv 2605.17458](https://arxiv.org/html/2605.17458v1)) — gần như đúng bài toán của khóa luận.
- Hướng **continual / interactive learning từ phản hồi nhiễu thời gian thực** ([Reinforced Interactive Continual Learning, arXiv 2505.09925](https://www.arxiv.org/abs/2505.09925)) phù hợp với bối cảnh DocVault: phản hồi của compliance officer đến rải rác, có thể nhiễu.

Vì sao RL "tăng tốc khả năng học" như thầy nói: classifier có giám sát chỉ học từ nhãn tĩnh; RLHF cho phép mô hình **học liên tục từ feedback vận hành thực** (mỗi lần compliance officer sửa nhãn là một tín hiệu reward), giúp mô hình thích nghi nhanh với chính sách và ngữ cảnh tổ chức mà không cần gán lại toàn bộ dataset.

### 3.3. MLOps, data drift / concept drift

- **Data drift**: phân phối đặc trưng đầu vào thay đổi. **Concept drift**: quan hệ giữa đặc trưng và nhãn thay đổi (cùng một loại tài liệu, hôm nay bị coi là mật, mai thì không) ([MLOps drift overview](https://mahajan-sameer.medium.com/mlops-series-introduction-to-mlops-data-drift-concept-drifts-and-how-to-handle-them-in-ml-e3821e05f948); [explainable concept drift, arXiv 2412.11308](https://arxiv.org/html/2412.11308v2)).
- Drift là **hiện tượng thống kê tất yếu**, không phải bug; phải **giám sát liên tục phân phối đặc trưng** ([7 strategies for drift detection](https://www.devopsroles.com/ultimate-strategies-master-mlops-model-drift/)).
- Mô hình vận hành: **giám sát → phát hiện drift → kích hoạt retraining tự động** ([automated monitoring & retraining, WJARR](https://wjarr.com/content/detecting-and-addressing-model-drift-automated-monitoring-and-real-time-retraining-ml); [event-triggered drift, Google Cloud](https://cloud.google.com/blog/topics/developers-practitioners/event-triggered-detection-data-drift-ml-workflows/)).
- Công cụ: **Kubeflow Pipelines** cho orchestration training/retraining + monitoring/retraining tự động ([Kubeflow model monitoring](https://datatalks.club/podcast/mlops-kubeflow-model-monitoring.html)); **MLflow** cho experiment tracking, model registry, versioning & deploy ([MLflow drift monitoring](https://medium.com/@mallickshuchismita/model-deployment-and-monitoring-for-drift-on-databricks-74b7474cb99b)).

Hệ quả thiết kế: dùng **MLflow** làm registry + tracking (nhẹ, dễ dựng cho khóa luận), và **Kubeflow Pipelines** làm orchestrator cho luồng retraining trên Kubernetes — vốn DocVault đã chạy trên K8s/ArgoCD nên tích hợp tự nhiên.

---

## 4. Nghiên cứu, so sánh và lựa chọn mô hình (đúng yêu cầu GVHD)

Thầy nhấn mạnh quy trình nghiên cứu chuẩn: **khảo sát các mô hình tốt nhất hiện nay → so sánh → chọn mô hình phù hợp đưa vào hệ thống → fine-tune lại để tối ưu**. Đây chính là phần "phương pháp nghiên cứu" tạo nên tính học thuật của khóa luận, không phải chỉ "lấy một mô hình rồi dùng".

### 4.1. Các họ mô hình ứng viên

Bài toán của DocVault là **phân loại tài liệu thành 4 mức** (`PUBLIC/INTERNAL/CONFIDENTIAL/SECRET`) — tức single-label/ordinal classification trên văn bản dài, song ngữ Việt–Anh. Ba họ mô hình đáng đưa vào khảo sát:

| Họ mô hình | Đại diện | Điểm mạnh | Điểm yếu / lưu ý |
| --- | --- | --- | --- |
| **Encoder-only (BERT family)** | DeBERTa-v3, ModernBERT, RoBERTa, XLM-R (đa ngữ) | Mạnh nhất cho phân loại; nhẹ, rẻ, suy luận nhanh; dễ fine-tune | Giới hạn độ dài ngữ cảnh (trừ ModernBERT 8k); cần dữ liệu có nhãn |
| **Decoder LLM fine-tuned** | Llama-3, Qwen-2.5, Mistral | Hiểu ngữ cảnh sâu, ngữ cảnh dài; có thể giải thích | Tốn tài nguyên 1–2 bậc; "nặng" so với nhu cầu phân loại |
| **LLM zero/few-shot (prompting)** | GPT/Claude/Gemini API hoặc LLM mở | Không cần train, triển khai nhanh; tốt làm baseline | Kém hơn fine-tuned ở phân loại; phụ thuộc API; rủi ro gửi tài liệu mật ra ngoài |

### 4.2. Phát hiện then chốt từ khảo sát (rất quan trọng để biện minh lựa chọn)

Các nghiên cứu 2025–2026 cho thấy một kết luận nhất quán, đi ngược trực giác "LLM càng to càng tốt":

- **Encoder fine-tuned (dòng BERT) thường ngang hoặc vượt LLM lớn ở bài toán phân loại, với chi phí thấp hơn 1–2 bậc** ([Multi-Objective Trade-offs, arXiv 2602.06370](https://arxiv.org/html/2602.06370v1); [Are We Really Making Progress?, arXiv 2204.03954](https://arxiv.org/html/2204.03954v6)).
- **LLM nhỏ được fine-tune vẫn vượt rõ rệt LLM lớn chạy zero-shot** trong phân loại văn bản ([Fine-Tuned 'Small' LLMs, arXiv 2406.08660](https://arxiv.org/html/2406.08660v1)).
- **DeBERTa-v3 vẫn rất cạnh tranh** về sample-efficiency và benchmark; **ModernBERT** nhanh hơn và hỗ trợ ngữ cảnh dài (8k token) — phù hợp tài liệu dài ([ModernBERT or DeBERTaV3?, arXiv 2504.08716](https://arxiv.org/html/2504.08716v1)).
- Với tài liệu **tiếng Việt + đa ngữ**, **XLM-RoBERTa** là lựa chọn cross-lingual mạnh ([BERT/RoBERTa/DeBERTa comparison](https://www.journals.uchicago.edu/doi/10.1086/730737)).
- Có nghiên cứu **đúng bài toán phân loại bảo mật tài liệu kèm XAI và so sánh LLM** ([Automated Document Security Classification with XAI and LLM Comparison, MDPI 2026](https://www.mdpi.com/2076-3417/16/11/5661/xml)) — có thể trích dẫn trực tiếp làm related work.

**Hệ quả lựa chọn cho DocVault:** chọn một **encoder fine-tuned đa ngữ làm mô hình chính** (ứng viên hàng đầu: ModernBERT cho tài liệu dài, hoặc XLM-R/DeBERTa-v3 cho song ngữ), và đặt **một LLM zero-shot làm baseline so sánh**. Điều này vừa đúng tinh thần "chọn mô hình tốt nhất", vừa hợp lý về bảo mật (chạy on-prem, không gửi tài liệu mật ra API ngoài) và chi phí — một lập luận rất thuyết phục trước hội đồng.

### 4.3. Giao thức so sánh (benchmark)

Để việc "so sánh" có tính khoa học, cần cố định điều kiện và đo trên cùng một tập:

1. **Cùng dataset, cùng split** train/val/test cho mọi mô hình ứng viên.
2. **Cùng bộ chỉ số**: accuracy, macro-F1, **recall lớp CONFIDENTIAL/SECRET** (ưu tiên không bỏ sót), confusion matrix, và chi phí (thời gian suy luận, VRAM).
3. **Baseline phân tầng**: từ cổ điển (TF-IDF + Logistic Regression/SVM) → encoder fine-tuned → LLM zero-shot → LLM fine-tuned. Có baseline cổ điển giúp chứng minh giá trị thực của mô hình sâu.
4. **Kiểm định ý nghĩa thống kê** (ví dụ paired test, nhiều seed) để kết luận không do may rủi.
5. Ghi lại toàn bộ thí nghiệm bằng **MLflow tracking** (đã nằm trong pipeline MLOps ở mục 5) — vừa phục vụ so sánh, vừa làm evidence.

### 4.4. Fine-tune để tối ưu mô hình đã chọn

Sau khi chọn được mô hình, **fine-tune** là bước tối ưu. Khảo sát cho thấy nên dùng **PEFT (Parameter-Efficient Fine-Tuning)** thay vì full fine-tune cho khóa luận:

- **LoRA** đạt F1 cao nhất trong các phương pháp PEFT cho phân loại văn bản low-resource ([PEFT comparative study, Frontiers in Big Data 2025](https://www.frontiersin.org/journals/big-data/articles/10.3389/fdata.2025.1677331/full)).
- **LoRA có thể đạt ngang full fine-tune** khi cấu hình đúng, với chi phí thấp hơn nhiều ([LoRA Without Regret, HuggingFace TRL](https://huggingface.co/docs/trl/lora_without_regret.md); [Thinking Machines Lab](https://thinkingmachines.ai/blog/lora/)).
- **QLoRA** cho phép fine-tune mô hình lớn trên một GPU đơn nhờ lượng tử hóa ([LoRA vs QLoRA 2026](https://www.index.dev/blog/top-ai-fine-tuning-tools-lora-vs-qlora-vs-full)).
- Lưu ý: có nghiên cứu chỉ ra LoRA tạo "intruder dimensions" có thể ảnh hưởng khi học liên tục ([LoRA vs Full FT: An Illusion of Equivalence](https://openreview.net/forum?id=PGNdDfsI6C)) — cần nhắc tới khi kết hợp với RL/continual learning ở mục 5.

Quy trình fine-tune đề xuất:
1. **Domain-adaptive pretraining (tùy chọn)**: tiếp tục pretrain trên kho tài liệu nội bộ (không nhãn) để mô hình quen domain.
2. **Supervised fine-tune (LoRA/QLoRA)** trên dữ liệu có nhãn 4 lớp → đây là "DL train trước" trong mục 5.2.
3. **Tối ưu siêu tham số** (learning rate, rank LoRA, epoch) có ghi vào MLflow.
4. **Chuyển sang RLHF** (mục 5.2) để tinh chỉnh theo feedback chuyên gia.

Như vậy, **bước fine-tune này nối thẳng vào pha "DL pretrain → RL fine-tune"** ở mục 5: mô hình được chọn → LoRA fine-tune (giám sát) → RLHF, rồi đưa vào pipeline MLOps chống drift.

---

## 5. Kiến trúc đề xuất (gắn vào DocVault hiện có)

### 5.1. Vị trí của AI trong luồng tài liệu

Chèn bước **AI sensitivity scoring** vào luồng upload/update metadata đã có (cùng chỗ với malware scan W-P1.1 và DLP W-P1.2):

```
Upload file → checksum → malware scan → trích xuất text (Apache Tika)
      → DLP regex (W-P1.2)
      → AI Sensitivity Classifier (DL + RL)  ← MỚI
      → gợi ý classification + độ tự tin (confidence)
      → nếu lệch với nhãn người dùng chọn: cảnh báo / yêu cầu xác nhận
      → ghi audit AI_CLASSIFICATION_SUGGESTED
      → lưu MinIO + tạo version
```

Nguyên tắc **human-in-the-loop**: AI **đề xuất**, không tự ý hạ/nâng mức bảo mật một cách im lặng. Quyết định cuối thuộc về người dùng/compliance officer, và mỗi lần họ chấp nhận/sửa là **một mẫu feedback cho RL**. Điều này vừa an toàn về tuân thủ, vừa tạo nguồn dữ liệu reward tự nhiên.

### 5.2. Hai pha học

| Pha | Mục tiêu | Đầu vào | Đầu ra |
| --- | --- | --- | --- |
| **DL pretrain** | Classifier 4 lớp baseline | Tài liệu đã gán nhãn (seed + lịch sử) | Mô hình base (warm start) |
| **RL fine-tune (RLHF)** | Tinh chỉnh theo policy tổ chức | Preference/feedback từ compliance officer | Reward model + policy cập nhật |

Reward được thiết kế **bất đối xứng theo rủi ro**: phạt nặng việc bỏ sót tài liệu mật (gán quá thấp) hơn là cảnh báo thừa, phản ánh đúng ưu tiên bảo mật.

### 5.3. Pipeline MLOps chống drift

```
                ┌─────────────────────────────────────────────┐
                │              DocVault runtime                │
                │  (inference: AI gợi ý classification)        │
                └───────────────┬─────────────────────────────┘
                                │ logs: input features, prediction,
                                │       user-final-label (ground truth muộn)
                                ▼
        ┌──────────────────────────────────────────┐
        │  Drift Monitor (Evidently / thống kê PSI, │
        │  KS-test trên phân phối đặc trưng + nhãn)  │
        └───────────────┬──────────────────────────┘
                drift?   │ yes (vượt ngưỡng)
                         ▼
        ┌──────────────────────────────────────────┐
        │  Kubeflow Pipeline: thu thập dữ liệu mới  │
        │  → relabel/preference → retrain DL        │
        │  → RL fine-tune → đánh giá trên holdout    │
        └───────────────┬──────────────────────────┘
              pass gate? │ yes
                         ▼
        ┌──────────────────────────────────────────┐
        │  MLflow Model Registry: version + stage   │
        │  (Staging → Production), canary/shadow     │
        └───────────────┬──────────────────────────┘
                         ▼
                 Triển khai mô hình mới (rollback nếu xấu)
```

Các thành phần:

- **Thu thập ground truth trễ**: nhãn cuối do người dùng/compliance chốt chính là nhãn đúng để so với dự đoán → đo concept drift trực tiếp (accuracy giảm theo thời gian).
- **Drift detector**: dùng test thống kê (PSI, Kolmogorov–Smirnov) trên phân phối embedding/đặc trưng và trên phân phối nhãn; có thể dùng thư viện Evidently.
- **Trigger retraining**: theo lịch (định kỳ) **và** theo sự kiện (drift vượt ngưỡng) — đúng mô hình event-triggered.
- **Đánh giá có cổng (gate)**: mô hình mới chỉ được promote nếu vượt baseline trên tập holdout (không để "retrain mù").
- **Triển khai an toàn**: canary/shadow deployment, có rollback — tái dùng đúng triết lý GitOps/ArgoCD của DocVault.

### 5.4. Ràng buộc bảo mật của chính AI (quan trọng cho hội đồng)

Bám theo W-P3 đã ghi trong kế hoạch:

- AI **chỉ đọc** tài liệu trong phạm vi quyền cho phép; không phá vỡ RBAC/ACL.
- Nếu compliance officer **không được xem nội dung file** thì pipeline AI cũng phải xử lý nội dung đó trong vùng tin cậy có kiểm soát, không lộ ra ngoài.
- **Mọi đề xuất/đánh giá của AI đều ghi audit** (`AI_CLASSIFICATION_SUGGESTED`, `AI_FEEDBACK_RECORDED`, `MODEL_RETRAINED`, `MODEL_PROMOTED`) → gắn vào audit hash-chain sẵn có, đảm bảo truy vết được.
- Dữ liệu training/feedback phải tuân thủ retention/privacy; cân nhắc **differential privacy / federated learning** nếu mở rộng (tham khảo [privacy-enabled financial text classification](https://ar5iv.labs.arxiv.org/html/2110.01643)).

---

## 6. Lộ trình thực hiện (đề xuất cho khóa luận)

| Giai đoạn | Nội dung | Sản phẩm |
| --- | --- | --- |
| **GĐ0 — Khảo sát & so sánh mô hình** | Nghiên cứu các mô hình tốt nhất hiện nay; benchmark trên cùng dataset (mục 4) | Bảng so sánh + quyết định chọn mô hình có dẫn chứng |
| **GĐ1 — Dữ liệu & baseline** | Thu thập/sinh dataset có nhãn; trích xuất text (Tika); fine-tune (LoRA) mô hình đã chọn làm baseline | Bộ dữ liệu + mô hình base + báo cáo metric (accuracy/F1 theo lớp) |
| **GĐ2 — RLHF** | Thiết kế cơ chế thu feedback từ compliance officer; train reward model; RL fine-tune | Mô hình tinh chỉnh + so sánh trước/sau RL |
| **GĐ3 — Tích hợp runtime** | Chèn inference vào luồng upload; human-in-the-loop; audit | Tính năng "AI gợi ý classification" chạy trong DocVault |
| **GĐ4 — MLOps & drift** | Dựng MLflow + Kubeflow; drift monitor; retraining pipeline; deploy có gate/rollback | Pipeline khép kín + demo drift → retrain → promote |
| **GĐ5 — Đánh giá & viết báo cáo** | Thí nghiệm drift mô phỏng; đo độ ổn định; phân tích bảo mật | Chương kết quả + đánh giá cho khóa luận |

Gợi ý phạm vi MVP cho khóa luận: có thể bắt đầu **MLflow** trước (nhẹ, đủ chứng minh tracking + registry + drift monitoring), và **mô phỏng drift** bằng cách dịch chuyển phân phối dữ liệu test — không nhất thiết phải dựng Kubeflow đầy đủ ngay nếu thời gian hạn chế. Kubeflow đưa vào như phần orchestration hoàn chỉnh khi đã ổn.

---

## 7. Tiêu chí đánh giá (để bảo vệ trước hội đồng)

- **Chất lượng phân loại**: accuracy, macro-F1, đặc biệt **recall của lớp CONFIDENTIAL/SECRET** (ưu tiên không bỏ sót tài liệu mật).
- **Hiệu quả của RL**: so sánh metric trước/sau RLHF; tốc độ thích nghi với feedback.
- **Năng lực chống drift**: thí nghiệm tiêm drift, đo độ suy giảm và **thời gian phục hồi** sau khi pipeline retrain.
- **An toàn vận hành**: tỉ lệ rollback đúng khi mô hình mới kém; không có rò rỉ dữ liệu mật qua pipeline AI.
- **Khả năng truy vết**: mọi quyết định AI có audit event tương ứng, kiểm chứng được trên hash-chain.

---

## 8. Rủi ro và lưu ý

- **Thiếu dữ liệu gán nhãn chất lượng** là rủi ro lớn nhất; cân nhắc sinh dữ liệu/seed có kiểm soát và tận dụng feedback vận hành.
- **RLHF phức tạp**: nếu thời gian hạn chế, có thể bắt đầu bằng **active learning / online learning** đơn giản trước, rồi nâng lên RL đầy đủ — vẫn giữ đúng tinh thần "pretrain rồi học tiếp từ feedback".
- **Chi phí hạ tầng Kubeflow** không nhỏ; ưu tiên MLflow + drift monitor cho MVP.
- **Đừng để AI thay quyền quyết định**: giữ human-in-the-loop để tránh rủi ro tuân thủ và để phần trình bày trước hội đồng thuyết phục về mặt an toàn.

---

## 9. Một câu định vị nên dùng trong báo cáo

> Ngoài việc là một hệ thống quản lý tài liệu bảo mật DevSecOps-first, DocVault được mở rộng thành một nền tảng có năng lực **tự đánh giá mức độ nhạy cảm tài liệu bằng AI**: một mô hình Deep Learning được tinh chỉnh bằng Reinforcement Learning từ phản hồi chuyên gia, vận hành dưới một pipeline MLOps có giám sát và chống suy giảm theo thời gian (drift) bằng MLflow/Kubeflow — toàn bộ tôn trọng đúng chính sách RBAC/ACL/audit sẵn có. Đây là phần đóng góp nghiên cứu nâng dự án từ một sản phẩm kỹ thuật lên một khóa luận có tính học thuật.

---

## Nguồn tham khảo

### Phân loại tài liệu nhạy cảm / NLP

- [Can pre-trained Transformers be used in detecting complex sensitive sentences? — Monsanto case study (arXiv 2203.06793)](https://arxiv.org/abs/2203.06793)
- [Improving Performance of Massive Text Real-Time Classification for Document Confidentiality Management (MDPI Applied Sciences 2024)](https://www.mdpi.com/2076-3417/14/4/1565/xml)
- [Automated Document Security Classification with XAI and LLM Comparison (MDPI 2026)](https://www.mdpi.com/2076-3417/16/11/5661/xml)
- [LLM-based document sensitivity classification with user-defined policies (US Patent App 20260134024)](https://patents.justia.com/patent/20260134024)
- [Building a GDPR-Compliant NLP Engine](https://mindcraft.ai/building-a-gdpr-compliant-nlp-engine/)
- [Privacy enabled Financial Text Classification using Differential Privacy and Federated Learning (arXiv 2110.01643)](https://ar5iv.labs.arxiv.org/html/2110.01643)

### So sánh & lựa chọn mô hình phân loại

- [Multi-Objective Trade-offs Between Fine-Tuned Encoders and LLM Prompting in Production (arXiv 2602.06370)](https://arxiv.org/html/2602.06370v1)
- [Are We Really Making Much Progress in Text Classification? A Comparative Review (arXiv 2204.03954)](https://arxiv.org/html/2204.03954v6)
- [Fine-Tuned 'Small' LLMs (Still) Significantly Outperform Zero-Shot Generative AI Models in Text Classification (arXiv 2406.08660)](https://arxiv.org/html/2406.08660v1)
- [A thorough benchmark of automatic text classification: from traditional approaches to LLMs (arXiv 2504.01930)](https://arxiv.org/html/2504.01930v1)
- [ModernBERT or DeBERTaV3? Examining Architecture and Data Influence (arXiv 2504.08716)](https://arxiv.org/html/2504.08716v1)
- [Advancing Single- and Multi-task Text Classification through LLM Fine-tuning (arXiv 2412.08587)](https://arxiv.org/html/2412.08587v1)
- [BERT, RoBERTa, or DeBERTa? Comparing Performance Across Transformer Models](https://www.journals.uchicago.edu/doi/10.1086/730737)

### Fine-tuning (PEFT / LoRA / QLoRA)

- [Parameter-efficient fine-tuning for low-resource text classification: LoRA, IA3, ReFT (Frontiers in Big Data 2025)](https://www.frontiersin.org/journals/big-data/articles/10.3389/fdata.2025.1677331/full)
- [LoRA Without Regret (HuggingFace TRL docs)](https://huggingface.co/docs/trl/lora_without_regret.md)
- [LoRA can match full fine-tuning when configured correctly (Thinking Machines Lab)](https://thinkingmachines.ai/blog/lora/)
- [LoRA vs QLoRA: Fine-Tuning Tools 2026](https://www.index.dev/blog/top-ai-fine-tuning-tools-lora-vs-qlora-vs-full)
- [LoRA vs Full Fine-tuning: An Illusion of Equivalence (OpenReview)](https://openreview.net/forum?id=PGNdDfsI6C)

### Reinforcement Learning from Human Feedback

- [ClaHF: A Human Feedback-inspired RL Framework for Classification (arXiv 2605.17458)](https://arxiv.org/html/2605.17458v1)
- [Reinforced Interactive Continual Learning via Real-time Noisy Human Feedback (arXiv 2505.09925)](https://www.arxiv.org/abs/2505.09925)
- [Reinforcement Learning from Human Feedback — overview (GeeksforGeeks)](https://www.geeksforgeeks.org/machine-learning/reinforcement-learning-from-human-feedback/)
- [RLHF Book (Lambert, arXiv 2504.12501)](https://arxiv.org/html/2504.12501v5)

### MLOps & Drift

- [MLOps Series: Data Drift, Concept Drift and how to handle them](https://mahajan-sameer.medium.com/mlops-series-introduction-to-mlops-data-drift-concept-drifts-and-how-to-handle-them-in-ml-e3821e05f948)
- [Explainable Concept Drift Detection (arXiv 2412.11308)](https://arxiv.org/html/2412.11308v2)
- [7 Ultimate Strategies to Master MLOps Model Drift Detection](https://www.devopsroles.com/ultimate-strategies-master-mlops-model-drift/)
- [Detecting and Addressing Model Drift: Automated Monitoring and Real-time Retraining (WJARR)](https://wjarr.com/content/detecting-and-addressing-model-drift-automated-monitoring-and-real-time-retraining-ml)
- [Event-triggered detection of data drift in ML workflows (Google Cloud)](https://cloud.google.com/blog/topics/developers-practitioners/event-triggered-detection-data-drift-ml-workflows/)
- [Kubeflow Pipelines, Model Monitoring & Automated Retraining (DataTalks.Club)](https://datatalks.club/podcast/mlops-kubeflow-model-monitoring.html)
- [Model Deployment and Monitoring for Drift on Databricks (MLflow)](https://medium.com/@mallickshuchismita/model-deployment-and-monitoring-for-drift-on-databricks-74b7474cb99b)
