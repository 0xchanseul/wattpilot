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

    O --> P[Wait Until Scheduled Time]
    P --> Q[Execute Mock Charging]
    Q --> R[Charging Completed]

    R --> S[Save Charging History]
    S --> T[Calculate Estimated Savings]
    T --> U[View Charging Result]
    U --> D
