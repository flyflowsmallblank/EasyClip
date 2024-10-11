package com.handsome.easyclip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.handsome.easyclip.wight.ClipViewLayout
import com.handsome.easyclip.wight.helper.ClipViewHelper


class SampleActivity : AppCompatActivity() {
    companion object{
        const val SAMPLE_ACTIVITY = 10000
    }
    private lateinit var mClipLayout : ClipViewLayout
    private lateinit var mBtnClip : Button
    private lateinit var mBtnChoosePhoto : Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)
        initView()
        initClick()
//        initConfig()
    }

    private fun initClick() {
        mBtnClip.setOnClickListener {
            mClipLayout.setImageUri(mClipLayout.clipBitMap(),true)
        }
        mBtnChoosePhoto.setOnClickListener {


            //权限判断
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                //申请READ_EXTERNAL_STORAGE权限
                ActivityCompat.requestPermissions(
                    this, arrayOf<String>(Manifest.permission.READ_EXTERNAL_STORAGE),
                    SAMPLE_ACTIVITY
                )
            } else {
                //跳转到相册
                gotoPhoto()
            }
        }
    }

    /**
     * 外部存储权限申请返回
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SAMPLE_ACTIVITY) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                gotoPhoto()
            }
        }
    }

    private fun setImageUri(uri: Uri?) {
        val left = 50f
        val top = 0f
        val bottom = 1000f
        val right = ClipViewHelper.getScreenWidth(this) - left
        mClipLayout.setClipRect(RectF(left, top, right, bottom))
        mClipLayout.setImageUri(uri,false)
    }

    private fun initView() {
        mClipLayout = findViewById(R.id.activity_sample_clip_view_layout)
        mBtnClip = findViewById(R.id.activity_sample_btn_clip)
        mBtnChoosePhoto = findViewById(R.id.activity_sample_btn_choose_photo)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when(requestCode){
            SAMPLE_ACTIVITY -> {
                /// 将图片放入图片裁剪中
                val uri = data?.data
                setImageUri(uri)
            }
        }
    }
    private fun gotoPhoto() {
        //跳转到调用系统图库
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(
            Intent.createChooser(intent, "请选择图片"),
            SAMPLE_ACTIVITY
        )
    }

}