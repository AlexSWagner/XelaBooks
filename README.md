# XelaBooks - Android AudioBook Manager

A modern Android application for managing and playing audiobooks with cloud storage integration.

## Features

### 📚 Audiobook Management
- **Library View**: Browse your audiobook collection with an intuitive grid layout
- **Single & Multi-Chapter Support**: Import audiobooks as single files or multiple chapter files
- **Custom Metadata**: Add titles, authors, descriptions, and cover images
- **Chapter Management**: Organize multi-chapter audiobooks with custom chapter titles

### ☁️ Cloud Integration
- **OneDrive Integration**: Browse, import, and download audiobooks directly from OneDrive
- **Batch Import**: Import entire folders of audio files at once
- **Authentication Management**: Secure Microsoft account authentication with proper scope handling

### 🎵 Audio Playback
- **Media Player**: Built-in audio player with standard controls (play, pause, skip, rewind)
- **Chapter Navigation**: Easy navigation between chapters in multi-chapter audiobooks
- **Progress Tracking**: Resume playback where you left off

### 🎨 User Interface
- **Material Design**: Clean, modern UI following Material Design guidelines
- **Bottom Navigation**: Easy access to Library, Import, and Dashboard sections
- **Settings Menu**: Accessible through toolbar gear icon
- **Dark Theme Support**: Automatic dark/light theme support

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Android Jetpack Navigation Component, View Binding
- **Database**: Room (SQLite)
- **Architecture**: MVVM with LiveData and ViewModel
- **Cloud Services**: Microsoft Graph API for OneDrive
- **Authentication**: Microsoft Authentication Library (MSAL)
- **Media**: Android MediaPlayer

## Project Structure

```
app/src/main/java/com/xelabooks/app/
├── adapter/           # RecyclerView adapters
├── data/             # Database, repositories, and cloud managers
├── model/            # Data models (AudioBook, Chapter)
├── ui/               # Fragments and UI components
│   ├── add/          # Add/Import audiobook functionality
│   ├── home/         # Library/Home screen
│   ├── onedrive/     # OneDrive browser and integration
│   ├── player/       # Audio player interface
│   └── settings/     # App settings
└── viewmodel/        # ViewModels for MVVM architecture
```

## Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API level 24 (Android 7.0) or higher
- Microsoft Azure account (for OneDrive integration)

### Azure App Registration Setup
1. Go to [Azure Portal](https://portal.azure.com) → App registrations
2. Create a new registration:
   - **Name**: XelaBooks AudioBook Manager
   - **Supported account types**: Personal Microsoft accounts only
   - **Redirect URI**: `msauth://com.xelabooks.app/your_package_signature_hash`
3. Note the **Application (client) ID**
4. Configure API permissions:
   - Microsoft Graph: `Files.Read.All`
   - Microsoft Graph: `User.Read`
   - Remove `Files.Read.Selected` if present (causes conflicts)

### Local Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/AlexSWagner/XelaBooks.git
   cd XelaBooks
   ```

2. Update the client ID in `app/src/main/res/raw/auth_config_single_account.json`:
   ```json
   {
     "client_id": "YOUR_AZURE_CLIENT_ID_HERE"
   }
   ```

3. Build and run the project in Android Studio

## Usage

### Importing Audiobooks

#### From Local Storage
1. Navigate to the **Import** tab
2. Select **Local** as the source
3. Choose **Single File** or **Multi-Chapter** mode
4. Select audio files using the file picker
5. Fill in title, author, and optional description
6. Add a cover image (optional)
7. Save to library

#### From OneDrive
1. Navigate to the **Import** tab
2. Select **OneDrive** as the source
3. Sign in to your Microsoft account (accept all permissions)
4. Browse folders and select individual files or import entire folders
5. Files are downloaded locally and added to your library
6. Fill in metadata and save

### Playing Audiobooks
1. Open your **Library** from the home tab
2. Tap on any audiobook to start playback
3. Use player controls to play/pause, skip chapters, or adjust position
4. Progress is automatically saved

## Known Issues & Solutions

### OneDrive Authentication
- **Issue**: "Account mismatch" error on sign-in
- **Solution**: Click "Use Different Account" instead of "Sign In"

### Files Not Appearing in Library
- **Issue**: Downloaded files don't automatically appear
- **Solution**: After import, manually fill in title/author fields and click "Save Book"

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Microsoft Graph API for OneDrive integration
- Android Jetpack libraries for modern Android development
- Material Design for UI/UX guidelines

---

**Author**: Alex Wagner  
**Repository**: [XelaBooks on GitHub](https://github.com/AlexSWagner/XelaBooks) 