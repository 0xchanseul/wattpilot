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

    I --> J[Retrieve Hourly Electricity Prices]
    J --> K[Calculate Required Energy and Charging Duration]
    K --> L[Find Cheapest Available Charging Hours]

    L --> M[Show Recommended Charging Schedule]

    M --> N{Confirm Schedule?}

    N -- No --> H
    N -- Yes --> O[Create Charging Reservation]

    O --> P[Wait Until Scheduled Time]
    P --> Q[Execute Mock Charging]
    Q --> R[Charging Completed]

    R --> S[Save Charging History]
    S --> T[Calculate Estimated Savings]
    T --> U[View Charging Result]
    U --> D