# TCP File Sharing Application with Congestion Control Visualization

A comprehensive educational tool that demonstrates TCP protocol mechanisms through real-time visualization of congestion control algorithms.

## 🎯 Project Overview

This JavaFX-based application implements TCP Reno, TCP Tahoe, and TCP Cubic congestion control algorithms while providing real-time visualization of network parameters. The system handles actual file transmission by segmenting files into packets, managing packet loss scenarios, and implementing fast retransmit mechanisms to provide users with an educational and practical understanding of TCP's behavior in network communication.

## 📋 Table of Contents

- [Problem Domain & Motivation](#problem-domain--motivation)
- [Objectives](#objectives)
- [Key Features](#key-features)
- [System Architecture](#system-architecture)
- [Technologies Used](#technologies-used)
- [Installation](#installation)
- [Usage](#usage)
- [Contributing](#contributing)
- [Team](#team)
- [License](#license)

## 🚀 Problem Domain & Motivation

Understanding TCP protocol behavior and congestion control mechanisms is crucial for network engineering students, yet traditional learning methods often lack practical visualization and hands-on experience. This project addresses several key challenges:

### Mo1: Educational Gap in TCP Visualization
Students often struggle to understand abstract TCP concepts like congestion control, window scaling, and packet loss recovery without visual representation of these dynamic processes.

### Mo2: Lack of Practical TCP Implementation Experience
Most networking courses focus on theoretical aspects without providing opportunities to implement and observe actual TCP behavior in real file transfer scenarios.

### Mo3: Need for Comparative Analysis Tools
There is a significant need for tools that allow students and network engineers to compare different TCP variants (Reno vs. Tahoe vs. Cubic) side-by-side to understand their performance characteristics and behavioral differences.

## 🎯 Objectives

### Ob1: Develop a Functional File Sharing Application
Create a robust JavaFX-based application that can reliably transfer files between devices while implementing core TCP functionalities including connection establishment, data transmission, and connection termination.

### Ob2: Implement and Visualize TCP Congestion Control
Integrate TCP Reno, TCP Tahoe, and TCP Cubic algorithms with comprehensive real-time visualization of network parameters including RTT, RWND, SSThresh, congestion window size, and throughput metrics over time.

### Ob3: Demonstrate Advanced TCP Features
Implement and showcase critical TCP mechanisms such as fast retransmit, fast recovery, duplicate ACK handling, timeout management, and packet loss detection through a multithreaded architecture for enhanced performance and accuracy.

## ✨ Key Features

### 📁 File Transfer Capability
- Support for various file types with real-time progress tracking
- Speed monitoring and configurable packet sizes
- Simulation of realistic network conditions

### 🔄 Multiple TCP Implementation
- **TCP Reno**: Includes fast retransmit and fast recovery
- **TCP Tahoe**: Classic implementation with slow start
- **TCP Cubic**: Modern high-speed implementation
- Side-by-side performance comparison

### 📊 Network Parameter Visualization
Real-time graphs displaying:
- Round Trip Time (RTT)
- Receive Window (RWND)
- Slow Start Threshold (SSThresh)
- Congestion window size
- Packet loss rates

### 🧵 Multithreaded Architecture
- Separate threads for RTT calculations
- Packet loss detection
- Acknowledgment processing
- GUI updates for responsive interface

### 🔧 Advanced TCP Mechanisms
- Fast retransmit implementation
- Fast recovery algorithms
- Duplicate ACK detection
- Timeout handling
- Congestion avoidance with visual indicators

### 🌐 Network Simulation Features
- Configurable packet loss rates
- Variable delays simulation
- Bandwidth limitations testing
- Different network scenario testing

## 🏗️ System Architecture

```
┌─────────────────────┐
│  JavaFX GUI         │
│  Interface          │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│  File Selection &   │
│  Configuration      │
└──────────┬──────────┘
           │
┌──────────▼──────────┐
│  TCP Algorithm      │
│  Selection          │
└─────┬────┬────┬─────┘
      │    │    │
   ┌──▼─┐ ┌▼─┐ ┌▼───┐
   │Reno│ │Cubic│ │Tahoe│
   └──┬─┘ └┬─┘ └┬───┘
      │    │    │
   ┌──▼────▼────▼─────┐
   │  Packetization   │
   │  Module          │
   └──────────┬───────┘
              │
   ┌──────────▼───────┐
   │  Sender Thread   │
   └─────┬─────┬──────┘
         │     │
    ┌────▼──┐ ┌▼────────┐
    │ACK    │ │RTT      │
    │Thread │ │Thread   │
    └────┬──┘ └┬────────┘
         │     │
    ┌────▼─────▼──┐
    │  Network    │
    │  Visualizer │
    └─────────────┘
```

## 🛠️ Technologies Used

- **JavaFX**: GUI framework and real-time visualization
- **Java Socket Programming**: TCP connections and packet transmission
- **Java Multithreading API**: Concurrent execution and thread-safe operations
- **JavaFX Charts API**: Dynamic network parameter visualization
- **Java NIO**: Efficient file handling and non-blocking I/O
- **Maven/Gradle**: Build automation and dependency management
- **JUnit**: Unit testing framework
- **Scene Builder**: Visual UI design tool

## 🔧 Installation

### Prerequisites
- Java 11 or higher
- JavaFX SDK
- Maven or Gradle

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/Jannatul-2003/TCP-File-Sharing-Application-with-Visualization.git
   cd TCP-File-Sharing-Application-with-Visualization
   ```

2. Install dependencies:
   ```bash
   mvn clean install
   ```
   or
   ```bash
   gradle build
   ```

3. Run the application:
   ```bash
   mvn javafx:run
   ```
   or
   ```bash
   gradle run
   ```

## 📖 Usage

1. **Launch the Application**: Start the JavaFX application
2. **Select Files**: Choose files to transfer using the file selection interface
3. **Configure Settings**: Set packet size, network conditions, and TCP algorithm
4. **Choose TCP Algorithm**: Select from Reno, Tahoe, or Cubic implementations
5. **Start Transfer**: Initiate file transfer and observe real-time visualizations
6. **Monitor Performance**: Watch RTT, congestion window, and throughput metrics
7. **Compare Algorithms**: Switch between different TCP variants to compare performance

## 🤝 Contributing

We welcome contributions to improve the TCP File Sharing Application! Here's how you can help:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Areas for Contribution
- Additional TCP algorithm implementations
- Enhanced visualization features
- Performance optimizations
- Bug fixes and testing
- Documentation improvements

## 👥 Team

**Course**: CSE 3111 - Computer Networks Lab  
**Institution**: University of Dhaka, Department of Computer Science and Engineering

**Team Members:**
- **Jannatul Ferdousi** (Roll: 08)
- **Anirban Roy Sourov** (Roll: 32)

**Instructors:**
- Mr. Palash Roy, Lecturer, Dept. of CSE, DU
- Mr. Jargis Ahmed, Lecturer, Dept. of CSE, DU  
- Dr. Ismat Rahman, Associate Professor, Dept. of CSE, DU

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🏆 Acknowledgments

- University of Dhaka, Department of Computer Science and Engineering
- Computer Networks Lab (CSE 3111) course instructors
- JavaFX community for excellent documentation and resources
- TCP/IP protocol research community

## 📊 Project Status

- **Current Phase**: Development
- **Submission Date**: June 26, 2025
- **Status**: Active Development

## 🔮 Future Enhancements

- Implementation of additional TCP variants (BBR, Vegas)
- Mobile application version
- Network topology visualization
- Advanced statistics and analytics
- Integration with real network testing tools

---

*This project serves as an educational tool for understanding TCP protocol behavior and congestion control mechanisms. It bridges the gap between theoretical networking concepts and practical implementation through visual representation and hands-on experience.*
