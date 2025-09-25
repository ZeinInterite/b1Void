# Context Menus and Popups

<cite>
**Referenced Files in This Document **   
- [context_menu_layout.xml](file://app/src/main/res/layout/context_menu_layout.xml)
- [enhanced_context_menu_layout.xml](file://app/src/main/res/layout/enhanced_context_menu_layout.xml)
- [popup_inspection.xml](file://app/src/main/res/layout/popup_inspection.xml)
- [file_context_menu.xml](file://app/src/main/res/menu/file_context_menu.xml)
- [FileAdapter.kt](file://app/src/main/java/com/example/b1void/adapters/FileAdapter.kt)
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt)
</cite>

## Table of Contents
1. [Introduction](#introduction)
2. [Core Menu Layouts](#core-menu-layouts)
3. [Popup Inspection Container](#popup-inspection-container)
4. [Menu Integration with File Adapter](#menu-integration-with-file-adapter)
5. [Context Menu Management in FileManagerActivity](#context-menu-management-in-filemanageractivity)
6. [Animation, Positioning, and Dismissal Behavior](#animation-positioning-and-dismissal-behavior)
7. [Menu Item Handling and Permission Checks](#menu-item-handling-and-permission-checks)
8. [Dynamic Menu State Management](#dynamic-menu-state-management)
9. [Usability Impact and Navigation Reduction](#usability-impact-and-navigation-reduction)
10. [Conclusion](#conclusion)

## Introduction
This document provides a comprehensive analysis of context-sensitive menus and transient popups within the Inspector application. It details the implementation of custom menu containers used for file operations such as rename, move, delete, and share. The focus is on `context_menu_layout.xml` and `enhanced_context_menu_layout.xml`, which are inflated from `file_context_menu.xml`, and their integration with `FileAdapter.kt`'s ViewHolder click listeners and `FileManagerActivity`'s context menu management system. Additionally, it covers the use of `popup_inspection.xml` in inspection workflows for displaying summary information or triggering actions. The documentation includes explanations of animation effects, positioning logic relative to anchor views, touch dismissal behavior, permission checks before destructive actions, and dynamic enabling/disabling of menu items based on file state.

## Core Menu Layouts

The application implements two distinct context menu layouts: `context_menu_layout.xml` and `enhanced_context_menu_layout.xml`. These serve as custom menu containers that provide quick access to file operations including move, share, rename, and delete. Both layouts are designed with a vertical LinearLayout structure, ensuring consistent alignment and spacing across different device configurations.

`context_menu_layout.xml` features a dark-themed UI with white text and icons, using a rounded background drawable. Each menu item consists of an icon (ImageView) paired with a descriptive label (TextView), arranged horizontally within clickable LinearLayout containers. The layout applies `?attr/selectableItemBackground` to indicate user interaction through ripple effects. Destructive actions like "Delete" are visually distinguished with red-colored text.

In contrast, `enhanced_context_menu_layout.xml` adopts a lighter theme with black text and primary color-tinted icons (`@color/colorPrimary`). It introduces additional visual separation between standard and destructive actions via a horizontal divider (`View`) element placed above the delete option. This layout also explicitly sets `clickable="true"` and `focusable="true"` attributes on each menu item container to ensure reliable touch response.

Both layouts support inflation from the same underlying menu resource `file_context_menu.xml`, which defines the logical structure of available actions without dictating visual presentation. This separation allows for flexible theming while maintaining consistent functionality.

**Section sources**
- [context_menu_layout.xml](file://app/src/main/res/layout/context_menu_layout.xml#L1-L118)
- [enhanced_context_menu_layout.xml](file://app/src/main/res/layout/enhanced_context_menu_layout.xml#L1-L128)
- [file_context_menu.xml](file://app/src/main/res/menu/file_context_menu.xml#L1-L12)

## Popup Inspection Container

The `popup_inspection.xml` layout serves as a container for transient popup windows used during inspection workflows. Unlike the action-oriented context menus, this layout is designed to display summary information or host interactive elements related to inspection tasks.

Currently implemented as a simple `ConstraintLayout` with match_parent dimensions, `popup_inspection.xml` provides a flexible foundation for hosting various UI components dynamically at runtime. Its full-screen height enables scrolling content if needed, while its width adapts to the parent container or anchoring view constraints.

Although minimal in its current definition, this layout is intended to be populated programmatically with inspection-specific data such as metadata summaries, status indicators, or action buttons. Future enhancements may include predefined slots for common inspection elements, improving consistency across different usage scenarios.

**Section sources**
- [popup_inspection.xml](file://app/src/main/res/layout/popup_inspection.xml#L1-L5)

## Menu Integration with File Adapter

The `FileAdapter.kt` class plays a crucial role in connecting RecyclerView items with context menu functionality. Within the `FileViewHolder` class, long-click interactions trigger the display of context menus by invoking the `onShowContextMenu` callback passed during adapter initialization.

Each `FileViewHolder` instance binds standard file properties—such as name, icon, play indicator for videos, and selection overlay—to corresponding UI elements defined in `item_file.xml`. The binding process differentiates between directories, images, videos, and other files, applying appropriate icons and visibility settings accordingly.

When a user performs a long press on a file item, the `onBindViewHolder` method registers this event through `holder.itemView.setOnLongClickListener`, calling `onShowContextMenu(file, holder.itemView)` with the target file and view reference. This pattern ensures that the context menu appears anchored to the specific item being interacted with, providing spatial continuity.

The adapter also supports selection mode, where multiple files can be selected simultaneously. During this mode, individual items display selection badges indicating order, and visual opacity changes reflect selection state—all managed through the `updateSelectionState` method.

**Section sources**
- [FileAdapter.kt](file://app/src/main/java/com/example/b1void/adapters/FileAdapter.kt#L1-L168)

## Context Menu Management in FileManagerActivity

The `FileManagerActivity.kt` class orchestrates context menu presentation and handling through several key methods. When a long-click event occurs, the `onItemLongClick` method is invoked, which calls `showPopupMenu(file, view)` to present options relevant to the selected file.

The `showPopupMenu` function creates a `PopupMenu` instance anchored to the provided view, inflating menu items from `R.menu.file_actions_menu`. This menu corresponds to the `file_context_menu.xml` resource but may contain additional entries depending on runtime conditions. A listener is attached to handle item selections by delegating to `onContextItemSelected`.

The `onContextItemSelected` override processes user choices by matching `itemId` values against known actions:
- `action_move`: Triggers `showMoveDialogForFile(file)`
- `action_delete`: Invokes `deleteFile(file)`
- `action_share`: Calls `shareFile(file)`
- `action_select_multiple`: Initiates multi-selection mode via `startSelectionMode(file)`

This centralized approach ensures consistent behavior across all file operations while allowing extensibility for future features. The activity maintains a reference to the currently targeted file (`currentFileForMenu`) to prevent race conditions during asynchronous operations.

**Section sources**
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L363-L386)
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L799-L839)

## Animation, Positioning, and Dismissal Behavior

Context menus and popups exhibit smooth animations to enhance user experience. When entering selection mode, the top toolbar animates into view using `slide_in_top.xml`, a predefined animation resource that translates the view from above into its final position. Conversely, when exiting selection mode, `slide_out_top.xml` animates the toolbar upward and out of sight.

Positioning of context menus follows Android's default `PopupMenu` behavior, appearing anchored to the triggering view with optimal placement calculated by the framework to avoid screen edge clipping. For custom bottom sheets like `MoveFilesBottomSheet`, positioning is handled by the `BottomSheetDialog` component, which slides up from the bottom of the screen.

Dismissal behavior is primarily gesture-driven. Touching outside the popup area automatically dismisses it, adhering to standard Android UX patterns. Additionally, explicit dismissal occurs when users select an action or cancel via back navigation. Selection mode exits either when all items are deselected or when the user taps the confirm button after performing batch operations.

**Section sources**
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L248-L254)
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L270-L275)

## Menu Item Handling and Permission Checks

All menu actions undergo validation before execution, particularly for destructive operations like deletion. Before deleting a file, the `deleteFile(file)` method attempts to move it to a designated trash directory using `FileManagerUtils.moveToTrash()`. If this operation fails due to permissions or I/O errors, a Toast notification alerts the user.

For sharing operations, the app checks whether the target is a directory or single file. Directories are zipped asynchronously before sharing, with error handling in place to catch compression failures. Single files are shared directly via `FileProvider`, ensuring proper URI permissions are granted using `FLAG_GRANT_READ_URI_PERMISSION`.

Batch operations such as deleting or moving multiple files iterate over the selected set, applying the same safety checks individually. Failed operations are tracked separately, and feedback is aggregated to inform users about partial success scenarios.

These safeguards prevent accidental data loss and ensure compliance with Android's scoped storage policies, especially when accessing external directories.

**Section sources**
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L450-L465)
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L540-L575)

## Dynamic Menu State Management

Menu availability is dynamically adjusted based on file type and selection state. In single-item context menus, all actions remain enabled unless restricted by file system permissions. However, in multi-selection mode, certain operations are conditionally disabled:

- **Move**: Available only when non-directory files are selected
- **Delete**: Enabled whenever any files are selected
- **Share**: Behavior changes based on content; image batches are shared natively, while mixed types are archived first

The `updateSelectionState()` method recalculates these states in real time, updating the confirmation button's enabled status and modifying the "Select All" toggle text between "Select All" and "Deselect All" based on current selection coverage.

Additionally, visual feedback reflects state changes: selected items show numbered badges, and the count is displayed in the selection toolbar. This immediate responsiveness helps users understand the consequences of their selections before committing actions.

**Section sources**
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L285-L305)

## Usability Impact and Navigation Reduction

The implementation of context menus and transient popups significantly reduces navigation depth for common file operations. Users can perform critical actions—rename, move, delete, share—directly from the file list without navigating to separate screens or opening additional dialogs.

By leveraging long-press gestures and anchored popups, the interface minimizes cognitive load and keeps users oriented within the current context. Batch operations further streamline workflows, allowing bulk management of files with minimal taps.

Transient popups like `popup_inspection.xml` extend this principle to inspection tasks, enabling rapid access to relevant data without leaving the primary workflow. Together, these components create a cohesive, efficient user experience that prioritizes speed and simplicity in file and inspection management.

**Section sources**
- [FileAdapter.kt](file://app/src/main/java/com/example/b1void/adapters/FileAdapter.kt#L1-L168)
- [FileManagerActivity.kt](file://app/src/main/java/com/example/b1void/activities/FileManagerActivity.kt#L1-L839)

## Conclusion

The context menu and popup system in the Inspector application demonstrates a well-structured approach to enhancing usability through direct manipulation interfaces. By combining customizable XML layouts, robust adapter integration, and intelligent state management, the app delivers a responsive and intuitive experience for managing files and inspections. The separation of concerns between layout definitions, business logic, and user interaction ensures maintainability and scalability, making it easier to introduce new features or modify existing ones without disrupting core functionality.