flowchart TD
    A[Start] --> B{Logged in?}

    B -- No --> C[Sign Up / Login]
    B -- Yes --> D[Dashboard]
    C --> D

    D --> E{EV registered?}

    E -- No --> F[Register EV]
    E -- Yes --> G[Create Charging Request]
    F --> G

    G --> H[Enter Charging Requirements]
    H --> H1[Current Battery Level]
    H --> H2[Target Battery Level]
    H --> H3[Required Completion Time]

    H1 --> I[Request Optimal Charging Schedule]
    H2 --> I
    H3 --> I

    I --> J[Preview API: Retrieve Hourly Prices]
    J --> K[Calculate Required Energy and Charging Duration]
    K --> L[Rank Continuous Charging Windows by Cost]

    L --> M[Show Up To 3 Candidates in a Modal - nothing saved]

    M --> N{Pick a candidate and confirm?}

    N -- No / change inputs --> H
    N -- Yes --> O[Schedule API: Recalculate, Validate Pick, Persist Plan + Slots + Schedule]

    O --> P[Schedule WAITING]
    P --> P1{User cancels before start?}
    P1 -- Yes --> P2[Schedule CANCELLED]
    P1 -- No --> Q{"1-min Scheduler: start time reached\nbefore the window closes?"}
    Q -- "Window closed first" --> Q1["Schedule/Session FAILED\n(MISSED_EXECUTION_WINDOW)"]
    Q -- Yes --> R[Scheduler calls Mock Charging: Start]
    R --> R1{Start succeeded?}
    R1 -- No --> R2[Schedule/Session FAILED]
    R1 -- Yes --> S[Schedule IN_PROGRESS / Session STARTED]

    S --> T[1-min Scheduler: end time reached]
    T --> U[Scheduler calls Mock Charging: Complete]
    U --> U1{Complete succeeded?}
    U1 -- No --> U2[Schedule/Session FAILED]
    U1 -- Yes --> V[Schedule COMPLETED / Session COMPLETED]

    V --> W["Store actual energy, cost, and savings\nfrom the plan/slot snapshot taken at confirmation"]
    W --> X[View Charging Result]
    X --> D
