import Testing
import Crossbeam

@Suite("Crossbeam Swift Export Tests")
struct CrossbeamExportTests {
    @Test("Backoff spin, snooze completion threshold, and reset work through Swift Export")
    func testBackoffLifecycle() {
        let backoff = utils.backoff.Backoff()
        #expect(!backoff.is_completed())

        for _ in 0 ..< 7 {
            backoff.spin()
        }
        #expect(!backoff.is_completed())

        for _ in 0 ..< 5 {
            backoff.snooze()
        }
        #expect(backoff.is_completed())

        backoff.reset()
        #expect(!backoff.is_completed())
    }
}
