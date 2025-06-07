Great question! Here's how we'd gather these metrics:

## **Data Sources & Collection:**

**📊 Research Progress (70%)**

- Track papers read vs. project goals set by user
- Monitor paper completion status (summarized, flashcards created, etc.)
- Calculate based on user-defined milestones and deadlines

**🧠 Knowledge Areas (8 topics)**

- Extract topics from paper abstracts/content using NLP
- Cluster similar topics together
- Track frequency of papers in each domain over time

**⚡ Study Efficiency (4.2x faster)**

- Benchmark: Average academic reads 2-3 papers/hour traditionally
- Track: Papers processed through Answer42 per hour
- Calculate ratio + user self-reported time savings surveys

**📈 Research Trends Chart**

- Log every paper upload with timestamp and extracted topics
- Group by week/month and topic categories
- Simple database query to generate time series data

**🎯 Focus Recommendations**

- Analyze topic overlap across recent papers
- Use citation analysis to find highly-cited papers in user's domains
- Compare user's reading list against citation networks

**📚 Citation Gap Analysis**

- Cross-reference user's papers against their citation lists
- Identify highly-cited papers that appear frequently but user hasn't read
- Use Semantic Scholar API for citation data

**🃏 Activity Tracking**

- Simple event logging: paper_uploaded, flashcards_created, chat_started
- Store in database with timestamps and user_id
- Query recent events for activity feed

Most of this is just **structured logging + basic analytics** on data we're already collecting!
