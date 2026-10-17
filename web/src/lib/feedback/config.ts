/** The feedback relay's URL, from config/feedback-url.txt. */
export const FEEDBACK_URL: string = __FEEDBACK_URL__.trim()

/** "Submit feedback" is hidden until the relay exists. */
export const feedbackEnabled = FEEDBACK_URL !== ''
