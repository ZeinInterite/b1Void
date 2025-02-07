
package com.example.b1void.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.example.b1void.R
import com.example.b1void.adapters.InspectorAdapter
import com.example.b1void.models.Inspector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AdmActivity : AppCompatActivity(), InspectorAdapter.OnItemLongClickListener, InspectorAdapter.OnItemShortClickListener {

    private lateinit var btnAddInspector: Button
    private lateinit var rvInspectors: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var inspectorAdapter: InspectorAdapter
    private var inspectorsList: MutableList<Inspector> = mutableListOf()

    private lateinit var popupWindow: PopupWindow
    private lateinit var popupView: View
    private lateinit var etInspectorName: EditText
    private lateinit var etInspectorCode: EditText
    private lateinit var btnAddPhoto: Button
    private lateinit var ivInspectorPreview: ImageView
    private lateinit var btnSaveInspector: Button

    private lateinit var editPopupWindow: PopupWindow
    private lateinit var editPopupView: View
    private lateinit var editEtInspectorName: EditText
    private lateinit var editEtInspectorCode: EditText
    private lateinit var editBtnAddPhoto: Button
    private lateinit var editIvInspectorPreview: ImageView
    private lateinit var editBtnSaveInspector: Button
    private lateinit var editBtnDeleteInspector: Button

    private var selectedImageUri: Uri? = null
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference

    private var currentEditInspector: Inspector? = null
    private var isEditMode: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adm)

        initFirebase()
        initViews()
        setupRecyclerView()
        loadInspectorsFromFirebase()
        initActivityResultLauncher()
        setupPopup()
        setupEditPopup()
        setupButtonListeners()
    }

    private fun initFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()
        storageRef = storage.reference
    }

    private fun initViews() {
        btnAddInspector = findViewById(R.id.btn_add_inspector)
        rvInspectors = findViewById(R.id.rv_inspectors)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
    }

    private fun setupRecyclerView() {
        rvInspectors.layoutManager = LinearLayoutManager(this)
        inspectorAdapter = InspectorAdapter(inspectorsList, this, this)
        rvInspectors.adapter = inspectorAdapter

        swipeRefreshLayout.setOnRefreshListener {
            loadInspectorsFromFirebase()
        }
    }

    private fun loadInspectorsFromFirebase() {
        val inspectorRef = database.reference.child("inspectors")

        // Инициальная загрузка данных
        inspectorRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                inspectorsList.clear()
                for (inspectorSnapshot in snapshot.children) {
                    val inspector = inspectorSnapshot.getValue(Inspector::class.java)
                    inspector?.let {
                        inspectorsList.add(it)
                    }
                }
                inspectorAdapter.updateList(inspectorsList)
                swipeRefreshLayout.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdmActivity, "Ошибка загрузки инспекторов", Toast.LENGTH_SHORT)
                    .show()
                swipeRefreshLayout.isRefreshing = false
            }
        })

        // Подписка на изменения данных
        inspectorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = mutableListOf<Inspector>()
                for (inspectorSnapshot in snapshot.children) {
                    val inspector = inspectorSnapshot.getValue(Inspector::class.java)
                    inspector?.let {
                        newList.add(it)
                    }
                }

                if(inspectorsList.size != newList.size){
                    inspectorsList.clear()
                    inspectorsList.addAll(newList)
                    inspectorAdapter.updateList(inspectorsList)
                }

                swipeRefreshLayout.isRefreshing = false

            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdmActivity, "Ошибка загрузки инспекторов", Toast.LENGTH_SHORT)
                    .show()
                swipeRefreshLayout.isRefreshing = false
            }
        })
    }

    private fun initActivityResultLauncher() {
        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data
                if (intent != null && intent.data != null) {
                    selectedImageUri = intent.data
                    if(isEditMode){
                        Glide.with(this).load(selectedImageUri).into(editIvInspectorPreview)
                    }else{
                        Glide.with(this).load(selectedImageUri).into(ivInspectorPreview)
                    }
                }
            }
        }
    }

    private fun setupPopup() {
        popupView = LayoutInflater.from(this).inflate(R.layout.pop_add_insp, null)
        popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        etInspectorName = popupView.findViewById(R.id.et_inspector_name)
        etInspectorCode = popupView.findViewById(R.id.et_inspector_code)
        btnAddPhoto = popupView.findViewById(R.id.btn_add_photo)
        ivInspectorPreview = popupView.findViewById(R.id.iv_inspector_preview)
        btnSaveInspector = popupView.findViewById(R.id.btn_save_inspector)
    }

    private fun setupEditPopup() {
        editPopupView = LayoutInflater.from(this).inflate(R.layout.pop_edit_insp, null)
        editPopupWindow = PopupWindow(
            editPopupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        editEtInspectorName = editPopupView.findViewById(R.id.edit_et_inspector_name)
        editEtInspectorCode = editPopupView.findViewById(R.id.edit_et_inspector_code)
        editBtnAddPhoto = editPopupView.findViewById(R.id.edit_btn_add_photo)
        editIvInspectorPreview = editPopupView.findViewById(R.id.edit_iv_inspector_preview)
        editBtnSaveInspector = editPopupView.findViewById(R.id.edit_btn_save_inspector)
        editBtnDeleteInspector = editPopupView.findViewById(R.id.edit_btn_delete_inspector)

        editPopupView.findViewById<ImageView>(R.id.edit_btn_close).setOnClickListener {
            editPopupWindow.dismiss()
        }
    }

    private fun setupButtonListeners() {
        btnAddInspector.setOnClickListener {
            showPopup()
        }

        btnAddPhoto.setOnClickListener {
            isEditMode = false
            openGallery()
        }

        btnSaveInspector.setOnClickListener {
            saveInspector()
        }

        popupWindow.setOnDismissListener {
            selectedImageUri = null
            Glide.with(this).load(R.drawable.def_insp_img).into(ivInspectorPreview)
        }
    }

    private fun showPopup() {
        popupWindow.showAtLocation(window.decorView, Gravity.CENTER, 0, 0)
    }


    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        activityResultLauncher.launch(intent)
    }

    private fun saveInspector() {
        val name = etInspectorName.text.toString().trim()
        val code = etInspectorCode.text.toString().trim()

        if (name.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        val inspectorId =
            database.reference.child("inspectors").push().key ?: return

        if (selectedImageUri != null) {
            savePhotoLocally(inspectorId, name, code)
        } else {
            saveInspectorToFirebase(inspectorId, name, code)
        }
        popupWindow.dismiss()
        etInspectorName.text.clear()
        etInspectorCode.text.clear()
    }

    private fun savePhotoLocally(inspectorId: String, name: String, code: String) {
        selectedImageUri?.let { uri ->
            val bitmap = uriToBitmap(uri)
            if(bitmap != null){
                val localPath = saveImageToInternalStorage(bitmap, inspectorId)
                if(localPath != null){
                    saveInspectorToFirebase(inspectorId, name, code, localPath)
                    Toast.makeText(this, "Инспектор добавлен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show()
                }

            }else {
                Toast.makeText(this, "Ошибка преобразования URI в Bitmap", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap, inspectorId: String): String? {
        val directory = getDir("inspector_images", Context.MODE_PRIVATE)
        val file = File(directory, "$inspectorId.jpg")

        try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            return file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    private fun saveInspectorToFirebase(
        inspectorId: String,
        name: String,
        code: String,
        localPhotoPath: String? = null
    ) {

        val inspector = Inspector(inspectorId, name, code, null, localPhotoPath)

        database.reference.child("inspectors")
            .child(inspectorId).setValue(inspector)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                } else {
                    Toast.makeText(this, "Ошибка добавления инспектора", Toast.LENGTH_SHORT).show()
                }
            }
    }


    override fun onItemLongClick(inspector: Inspector) {
        currentEditInspector = inspector
        showEditPopup(inspector)
    }

    private fun showEditPopup(inspector: Inspector) {
        editEtInspectorName.setText(inspector.name)
        editEtInspectorCode.setText(inspector.code)

        if(inspector.localPhotoPath != null){
            val bitmap = BitmapFactory.decodeFile(inspector.localPhotoPath)
            Glide.with(this).load(bitmap).into(editIvInspectorPreview)
        }else{
            Glide.with(this).load(R.drawable.def_insp_img).into(editIvInspectorPreview)
        }


        editBtnAddPhoto.setOnClickListener{
            isEditMode = true
            openGallery()
        }

        editBtnSaveInspector.setOnClickListener {
            updateInspector(inspector)
        }

        editBtnDeleteInspector.setOnClickListener {
            deleteInspector(inspector)
        }


        editPopupWindow.showAtLocation(window.decorView, Gravity.CENTER, 0, 0)
    }

    private fun updateInspector(inspector: Inspector) {
        val newName = editEtInspectorName.text.toString().trim()
        val newCode = editEtInspectorCode.text.toString().trim()

        if (newName.isEmpty() || newCode.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedInspector = inspector.copy(name = newName, code = newCode)

        if(selectedImageUri != null){
            updatePhotoLocally(updatedInspector)
        } else {
            updateInspectorToFirebase(updatedInspector)
        }

        editPopupWindow.dismiss()
    }

    private fun updatePhotoLocally(inspector: Inspector){
        selectedImageUri?.let { uri ->
            val bitmap = uriToBitmap(uri)
            if(bitmap != null){
                val localPath = saveImageToInternalStorage(bitmap, inspector.id)
                if(localPath != null){
                    val updatedInspector = inspector.copy(localPhotoPath = localPath)
                    updateInspectorToFirebase(updatedInspector)
                    Toast.makeText(this, "Инспектор изменен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка сохранения фото", Toast.LENGTH_SHORT).show()
                }
            }else {
                Toast.makeText(this, "Ошибка преобразования URI в Bitmap", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateInspectorToFirebase(updatedInspector: Inspector){
        database.reference.child("inspectors").child(updatedInspector.id)
            .setValue(updatedInspector)
            .addOnCompleteListener { task ->
                if(task.isSuccessful){
                    Toast.makeText(this, "Инспектор изменен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка изменения инспектора", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun deleteInspector(inspector: Inspector) {
        database.reference.child("inspectors").child(inspector.id)
            .removeValue()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val file = File(inspector.localPhotoPath)
                    if (file.exists()) {
                        file.delete()
                    }
                    Toast.makeText(this, "Инспектор удален", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка удаления инспектора", Toast.LENGTH_SHORT).show()
                }
            }

        editPopupWindow.dismiss()
    }

    override fun onItemShortClick(inspector: Inspector) {
        val intent = Intent(this, ShowInspectionActivity::class.java)
        intent.putExtra("inspectorId", inspector.id)
        intent.putExtra("inspectorName", inspector.name)
        intent.putExtra("inspectorCode", inspector.code)
        intent.putExtra("inspectorPhoto", inspector.localPhotoPath)
        startActivity(intent)
    }
}
