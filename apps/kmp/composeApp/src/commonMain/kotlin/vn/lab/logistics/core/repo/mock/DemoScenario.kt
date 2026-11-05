package vn.lab.logistics.core.repo.mock

/**
 * Kịch bản cho repo GIẢ LẬP — chỉ tồn tại để demo/test 4 trạng thái khi chưa có backend.
 *
 * Đây là "flag bật mock" đúng luật (agents/fe/AGENT.md §3.2: mock phải nằm sau interface
 * `Repository` và bật bằng flag). Wave B: xoá enum này khỏi đường chính, thay impl thật trong
 * nơi lắp ráp (`App.kt` → sau này là Koin module).
 */
enum class DemoScenario(val label: String) {

    /** Có dữ liệu, độ trễ ~0.8s để thấy trạng thái Loading. */
    Normal("Bình thường"),

    /** Tải xong nhưng danh sách rỗng → trạng thái Empty. */
    Empty("Không có dữ liệu"),

    /** Repo trả lỗi tiếng Việt → trạng thái Error + nút Thử lại. */
    Error("Lỗi máy chủ"),

    /** Trễ 2.5s → thấy rõ Loading, giống mạng 3G ngoài hiện trường. */
    Slow("Mạng chậm"),
}
