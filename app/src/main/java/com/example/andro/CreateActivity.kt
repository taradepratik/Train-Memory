package com.example.andro

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PatternMatcher
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.andro.models.BoardSize
import com.example.andro.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object{
        private const val TAG = "CreateActivity"
        private const val PICK_PHOTO_CODE = 655
        private const val READ_EXTERNAL_PHOTO_CODE = 248
        private const val READ_PHOTO_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val MINI_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14

    }
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var pbUploading: ProgressBar

    private lateinit var boardSize: BoardSize
    private var numImageRequired =-1
    private lateinit var adapter: ImagePickerAdapter
    private val chosenImageUris = mutableListOf<Uri>()     //Uri: Uniform resource identifier
    private val storage = Firebase.storage
    private val db = Firebase.firestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImageRequired = boardSize.getNumsPairs()
        supportActionBar?.title = "Choose pics (0 / $numImageRequired)"

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)                      // add back button

        btnSave.setOnClickListener{
            saveDataToFirebase()
        }
        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))        // Restrict size of game name till 14 characters
        etGameName.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        })
        adapter = ImagePickerAdapter(this, chosenImageUris, boardSize, object : ImagePickerAdapter.ImageClickListener{
            override fun onPlaceholderClicked() {
                if(isPermissionGranted(this@CreateActivity, READ_PHOTO_PERMISSION)){
                    launchIntentForPhotos()
                }else{
                    requestPermission(this@CreateActivity, READ_PHOTO_PERMISSION, READ_EXTERNAL_PHOTO_CODE)
                }
            }

        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())

    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if(requestCode == READ_EXTERNAL_PHOTO_CODE){
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                launchIntentForPhotos()
            }else{
                Toast.makeText(this, "In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    // add back button
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode!= PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null){
            Log.w(TAG, "Did not get data back from the launched activity, user likely canceled flow")
            return
        }
        val selectedUri = data.data
        val clipData = data.clipData
        if(clipData != null){
            Log.i(TAG, "clipData numImage ${clipData.itemCount}: $clipData")
            for(i in 0 until clipData.itemCount){
                val clipItem  = clipData.getItemAt(i)
                if(chosenImageUris.size < numImageRequired){
                    chosenImageUris.add(clipItem.uri)
                }
            }
        }
        else if(selectedUri != null){
            Log.i(TAG, "data: $selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics (${chosenImageUris.size}/ $numImageRequired)"
        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun saveDataToFirebase() {
        Log.i(TAG, "save data to firebase")
        btnSave.isEnabled = false
        val customGameName = etGameName.text.toString()
        // Checking for it already have same game name file (to avoid game data overriding)
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if(document!= null && document.data!= null){
                AlertDialog.Builder(this)
                    .setTitle("Name Taken")
                    .setMessage("Game already exist with the name '$customGameName'. Please choose another name")
                    .setPositiveButton("Ok", null)
                    .show()
                btnSave.isEnabled = true
            }
            else{
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Encounter Error while saving memory game", exception)
            Toast.makeText(this, "Encounter Error while saving memory game", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
        }

    }

    private fun handleImageUploading(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()    // might be a problem with a referencing
        Log.i(TAG, "saveDataToFirebase")
        for((index, photoUrl) in chosenImageUris.withIndex()){
            val imageByteArray = getImageByteArray(photoUrl)
            val filePath = "image/$gameName/${System.currentTimeMillis()}-$index.jpg"
            // Upload photo
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask { photoUploadTask ->
                    Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl
                }.addOnCompleteListener { downloadUrlTask ->
                    if(!downloadUrlTask.isSuccessful){
                        Log.i(TAG, "Exception with firebase storage", downloadUrlTask.exception)
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_LONG).show()
                        didEncounterError = true;
                        return@addOnCompleteListener
                    }
                    if(didEncounterError){
                        pbUploading.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    pbUploading.progress = uploadedImageUrls.size * 100/ chosenImageUris.size
                    Log.i(TAG, "Finished uploading $photoUrl, num uploaded ${uploadedImageUrls.size}")
                    if(uploadedImageUrls.size == chosenImageUris.size){
                        handleAllImageUploaded(gameName, uploadedImageUrls)
                    }
                }
        }

    }


    private fun handleAllImageUploaded(gameName: String, imageUrls: MutableList<String>) {
        // to upload this information to firestore
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))                                                      // made change append s to image
            .addOnCompleteListener { gameCreationTask->
                pbUploading.visibility = View.GONE
                if(!gameCreationTask.isSuccessful){
                    Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }
                Log.i(TAG, "Successfully created game $gameName")
                AlertDialog.Builder(this)
                    .setTitle("Upload completed lets play your game: '$gameName'")
                    .setPositiveButton("Ok"){_, _ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }

    }

    private fun getImageByteArray(photoUrl: Uri): ByteArray {                               // Downgrade quality of images that will upload on firebase
        val originalBitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){          //If android version is equal or above android pie
            val source = ImageDecoder.createSource(contentResolver, photoUrl)
            ImageDecoder.decodeBitmap(source)
        }else{                                                                          //else android version is below android pie
            MediaStore.Images.Media.getBitmap(contentResolver, photoUrl)
        }
        Log.i(TAG, "Original width ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaleBitmap = BitmapScaler.scalerToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scale width ${scaleBitmap.width} and height ${scaleBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaleBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if(chosenImageUris.size != numImageRequired){
            return false
        }
        if(etGameName.text.isBlank() || etGameName.text.length < MINI_GAME_NAME_LENGTH){
            return false
        }
        return true
    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
    intent.type = "image/*"                                                 //To get only images and not shown any other type of file
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)            // EXTRA_ALLOW_MULTIPLE is a key; to allow user to pick multiple images
        startActivityForResult(Intent.createChooser(intent, "Choose pics"), PICK_PHOTO_CODE)
    }
}