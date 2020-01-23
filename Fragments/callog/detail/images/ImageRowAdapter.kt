package com.nn.my2ncommunicator.main.callog.detail.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.widget.RecyclerView
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.nn.my2ncommunicator.App
import com.nn.my2ncommunicator.R
import kotlinx.android.synthetic.main.item_image_preview.view.*

class ImageRowAdapter(val callLogId: Long, private val onClickListener: OnItemClickListener, currentItem: Int, private val selectedImages: MutableList<String>) : RecyclerView.Adapter<ImageRowAdapter.ImagePreviewItemHolder>() {

    private var selectMode: Boolean = selectedImages.size > 0

    private val images: MutableList<String> = App.getInstance().snapshotService.getImages(callLogId).toMutableList()

    private val bitmaps: SparseArray<Bitmap?> = SparseArray(images.size)

    var currentItem: Int = currentItem
        set(value) {
            if (field != value) {
                val previous = field
                field = value
                notifyItemChanged(previous)
                notifyItemChanged(currentItem)
            }
        }

    fun onItemsDeleted(remainingImages: MutableList<String>) {
        bitmaps.clear()
        images.clear()
        images.addAll(remainingImages)
        exitSelectMode()
    }

    fun getSelectedImages(): List<String> {
        if (selectMode) {
            return ArrayList(selectedImages)
        } else {
            return listOf(images[currentItem])
        }
    }

    fun isInSelectMode(): Boolean {
        return selectMode
    }

    fun exitSelectMode() {
        selectMode = false
        selectedImages.clear()
        notifyDataSetChanged()
    }

    fun enterSelectMode(position: Int) {
        selectMode = true
        currentItem = position
        selectedImages.add(images.get(position))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ImagePreviewItemHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_image_preview, viewGroup, false)
        return ImagePreviewItemHolder(view)
    }

    override fun onBindViewHolder(imagePreviewItemHolder: ImagePreviewItemHolder, position: Int) {
        initBitmap(imagePreviewItemHolder.image, position)
        initCurrentItem(imagePreviewItemHolder.image, position)
        initClickListeners(imagePreviewItemHolder.view, position)
        initSelectMode(imagePreviewItemHolder, position)
    }

    private fun initBitmap(image: ImageView, position: Int) {
        var bitmap = bitmaps.get(position)
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeFile(images.get(position))
            bitmaps.put(position, bitmap)
        }
        image.setImageBitmap(bitmap)
    }

    private fun initCurrentItem(image: ImageView, position: Int) {
        if (position == currentItem) {
            image.setBackgroundResource(R.drawable.bg_image_row_preview)
        } else {
            image.setBackgroundResource(0)
        }
    }

    private fun initClickListeners(view: View, position: Int) {
        view.setOnClickListener {
            if (selectMode) {
                val selectedImage = images.get(position)
                if (selectedImages.contains(selectedImage)) {
                    selectedImages.remove(selectedImage)
                } else {
                    selectedImages.add(selectedImage)
                }
                if (selectedImages.size == 0) {
                    exitSelectMode()
                } else {
                    notifyItemChanged(position)
                }
            }
            currentItem = position
            onClickListener.onPreviewClick(position)
        }
        view.setOnLongClickListener {
            enterSelectMode(position)
            onClickListener.onPreviewClick(position)
            true
        }
    }

    private fun initSelectMode(imagePreviewItemHolder: ImagePreviewItemHolder, position: Int) {
        if (selectMode == false) {
            imagePreviewItemHolder.checkIcon.visibility = View.GONE
        } else {
            val currentImage = images[position]
            if (selectedImages.contains(currentImage)) {
                imagePreviewItemHolder.checkIcon.setImageResource(R.drawable.ic_check_circle)
            } else {
                imagePreviewItemHolder.checkIcon.setImageResource(R.drawable.ic_uncheck_circle)
            }
            imagePreviewItemHolder.checkIcon.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int {
        return images.count()
    }

    class ImagePreviewItemHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val image = view.imageView as ImageView
        val imageContainer = view.imageContainer!!
        val checkIcon = view.checkIcon as ImageView
    }

    interface OnItemClickListener {
        fun onPreviewClick(position: Int)
    }
}
